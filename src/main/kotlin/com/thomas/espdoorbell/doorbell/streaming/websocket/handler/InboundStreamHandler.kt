package com.thomas.espdoorbell.doorbell.streaming.websocket.handler

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.StreamPacket
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.parseStreamPacket
import com.thomas.espdoorbell.doorbell.streaming.pipeline.DeviceStreamManager
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.nio.ByteBuffer
import java.util.*

@Component
class InboundStreamHandler(
    private val deviceStreamManager: DeviceStreamManager,
    private val deviceService: DeviceService,
    private val meterRegistry: MeterRegistry
) : WebSocketHandler {

    private val logger = LoggerFactory.getLogger(InboundStreamHandler::class.java)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val deviceIdStr = session.handshakeInfo.uri.path.split("/").lastOrNull()
        if (deviceIdStr == null) {
            logger.error("Invalid WebSocket path: ${session.handshakeInfo.uri.path}")
            return session.close()
        }

        val deviceId: UUID
        try {
            deviceId = UUID.fromString(deviceIdStr)
        } catch (_: IllegalArgumentException) {
            logger.error("Invalid device ID format: $deviceIdStr")
            return session.close()
        }

        logger.info("Inbound connection established for device $deviceId, session ${session.id}")

        return mono {
            try {
                val deviceKey = session.handshakeInfo.headers.getFirst("X-Device-Key")
                if (deviceKey.isNullOrBlank()) {
                    logger.warn("Missing X-Device-Key header for device $deviceId")
                    throw SecurityException("Missing device key")
                }

                val isValidKey = deviceService.verifyDeviceKey(deviceId, deviceKey)
                if (!isValidKey) {
                    logger.warn("Invalid device key for device $deviceId")
                    throw SecurityException("Invalid device key")
                }

                deviceStreamManager.registerInbound(deviceId, session.id)
                
                logger.info("Device $deviceId authenticated and registered")
            } catch (e: Exception) {
                logger.error("Failed to authenticate or register device $deviceId", e)
                throw e
            }
        }.flatMap {
            session.receive()
                .doOnNext { message ->
                    if (message.type == WebSocketMessage.Type.BINARY) {
                        try {
                            val packet = message.payload.let {
                                val buffer = ByteBuffer.allocate(it.readableByteCount())
                                it.toByteBuffer(buffer)
                                buffer.flip()
                                buffer.parseStreamPacket()
                            }

                            if (packet == null) {
                                logger.warn("Failed to parse packet from device $deviceId")
                                return@doOnNext
                            }

                            val ptsMicros = packet.ptsMillis * 1000L

                            logger.debug(
                                "Received {} packet from device {}: pts={}ms, size={} bytes",
                                packet.type.name,
                                deviceId,
                                packet.ptsMillis,
                                packet.payload.size
                            )

                            meterRegistry.counter(
                                "stream.inbound.frames",
                                "device_id", deviceId.toString(),
                                "type", packet.type.name
                            ).increment()

                            when (packet.type) {
                                StreamPacket.PacketType.VIDEO -> {
                                    deviceStreamManager.feedVideoFrame(
                                        deviceId = deviceId,
                                        jpegData = packet.payload,
                                        pts = ptsMicros
                                    )
                                }
                                StreamPacket.PacketType.AUDIO -> {
                                    deviceStreamManager.feedAudioFrame(
                                        deviceId = deviceId,
                                        aacData = packet.payload,
                                        pts = ptsMicros
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error processing packet from device $deviceId", e)
                        }
                    }
                }
                .doOnError { error ->
                    logger.error("Error in inbound stream for device $deviceId", error)
                }
                .publishOn(Schedulers.boundedElastic())
                .doFinally { _ ->
                    logger.info("Inbound connection closed for device $deviceId, session ${session.id}")
                    // Unregister inbound connection
                    mono {
                        try {
                            deviceStreamManager.unregisterInbound(deviceId, session.id)
                        } catch (e: Exception) {
                            logger.error("Error unregistering inbound for device $deviceId", e)
                        }
                    }.subscribe()
                }
                .then()
        }.onErrorResume { error ->
            logger.error("Error in inbound handler for device $deviceId", error)
            session.close()
        }
    }
}

