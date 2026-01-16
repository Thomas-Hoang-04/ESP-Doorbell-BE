package com.thomas.espdoorbell.doorbell.streaming.websocket.handler

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.pipeline.DeviceStreamManager
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.StreamPacket
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.parseStreamPacket
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.netty.channel.AbortedException
import java.nio.ByteBuffer

@Component
class InboundStreamHandler(
    private val deviceStreamManager: DeviceStreamManager,
    private val deviceService: DeviceService,
    private val meterRegistry: MeterRegistry
) : WebSocketHandler {

    private val logger = LoggerFactory.getLogger(InboundStreamHandler::class.java)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val deviceIdentifier = session.handshakeInfo.uri.path.split("/").lastOrNull()
        if (deviceIdentifier.isNullOrBlank()) {
            logger.error("Invalid WebSocket path: ${session.handshakeInfo.uri.path}")
            return session.close()
        }

        logger.info("Inbound connection attempt for device $deviceIdentifier, session ${session.id}")

        return mono {
            try {
                val deviceKey = session.handshakeInfo.headers.getFirst("X-Device-Key")
                if (deviceKey.isNullOrBlank()) {
                    logger.warn("Missing X-Device-Key header for device $deviceIdentifier")
                    throw SecurityException("Missing device key")
                }

                val isValidKey = deviceService.verifyDeviceKey(deviceIdentifier, deviceKey)
                if (!isValidKey) {
                    logger.warn("Invalid device key for device $deviceIdentifier")
                    throw SecurityException("Invalid device key")
                }

                val device = deviceService.getDeviceEntityByIdentifier(deviceIdentifier)
                val deviceIdUUID = device.id!!
                
                deviceStreamManager.registerInbound(deviceIdUUID, session.id)
                logger.info("Device $deviceIdentifier authenticated and registered with UUID $deviceIdUUID")
                
                deviceIdUUID
            } catch (e: Exception) {
                logger.error("Failed to authenticate or register device $deviceIdentifier", e)
                throw e
            }
        }.flatMap { deviceIdUUID ->
            session.receive()
                .doOnNext { message ->
                    if (message.type == WebSocketMessage.Type.BINARY) {
                        val payload = message.payload
                        val payloadSize = payload.readableByteCount()
                        if (payloadSize == 0) {
                            return@doOnNext
                        }
                        
                        try {
                            val byteArray = ByteArray(payload.readableByteCount())
                            payload.read(byteArray)
                            val packet = ByteBuffer.wrap(byteArray).parseStreamPacket()

                            if (packet == null) {
                                logger.warn("Failed to parse packet from device $deviceIdentifier (size=$payloadSize)")
                                return@doOnNext
                            }

                            val ptsMicros = packet.ptsMillis * 1000L

                            logger.info(
                                "Received {} packet from device {}: pts={}ms, size={} bytes",
                                packet.type.name,
                                deviceIdentifier,
                                packet.ptsMillis,
                                packet.payload.size
                            )

                            meterRegistry.counter(
                                "stream.inbound.frames",
                                "device_id", deviceIdentifier,
                                "type", packet.type.name
                            ).increment()

                            when (packet.type) {
                                StreamPacket.PacketType.VIDEO -> {
                                    deviceStreamManager.feedVideoFrame(
                                        deviceId = deviceIdUUID,
                                        jpegData = packet.payload,
                                        pts = ptsMicros
                                    )
                                }
                                StreamPacket.PacketType.AUDIO -> {
                                    deviceStreamManager.feedAudioFrame(
                                        deviceId = deviceIdUUID,
                                        aacData = packet.payload,
                                        pts = ptsMicros
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error processing packet from device $deviceIdentifier", e)
                        }
                    }
                }
                .doOnError { error ->
                    if (error is AbortedException ||
                        error.message?.contains("closed", ignoreCase = true) == true) {
                        logger.info("Inbound connection closed unexpectedly for device $deviceIdentifier")
                    } else {
                        logger.error("Error in inbound stream for device $deviceIdentifier", error)
                    }
                }
                .publishOn(Schedulers.boundedElastic())
                .doFinally { _ ->
                    logger.info("Inbound connection closed for device $deviceIdentifier, session ${session.id}")
                    mono {
                        try {
                            deviceStreamManager.unregisterInbound(deviceIdUUID, session.id)
                        } catch (e: Exception) {
                            logger.error("Error unregistering inbound for device $deviceIdentifier", e)
                        }
                    }.subscribe()
                }
                .then()
        }.onErrorResume { error ->
            if (error is AbortedException ||
                error.message?.contains("closed", ignoreCase = true) == true) {
                logger.info("Inbound handler: connection closed for device $deviceIdentifier")
            } else {
                logger.error("Error in inbound handler for device $deviceIdentifier", error)
            }
            session.close()
        }
    }
}

