package com.thomas.espdoorbell.doorbell.streaming.websocket.handler

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.StreamPacket
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.parseStreamPacket
import com.thomas.espdoorbell.doorbell.streaming.pipeline.DeviceStreamManager
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

/**
 * WebSocket handler for inbound ESP32 device connections
 * Receives binary packets with video/audio data and feeds them to the transcoding pipeline
 */
// TODO: Validate device authentication token from WebSocket headers
// TODO: Rate limit connections per device (prevent DoS)
// TODO: Add metrics for frames received per second
// TODO: Handle WebSocket ping/pong for connection health
@Component
class InboundStreamHandler(
    private val deviceStreamManager: DeviceStreamManager,
    private val deviceService: DeviceService
) : WebSocketHandler {

    private val logger = LoggerFactory.getLogger(InboundStreamHandler::class.java)

    override fun handle(session: WebSocketSession): Mono<Void> {
        // Extract deviceId from path config
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

        // Validate device exists and registers inbound connection
        return mono {
            try {
                // Validate device exists
                deviceService.getDevice(deviceId)
                
                // Register inbound connection (enforces single ESP32 per device)
                deviceStreamManager.registerInbound(deviceId, session.id)
                
                logger.info("Device $deviceId validated and registered")
            } catch (e: Exception) {
                logger.error("Failed to validate or register device $deviceId", e)
                throw e
            }
        }.flatMap {
            // Handle incoming binary messages
            session.receive()
                .doOnNext { message ->
                    if (message.type == WebSocketMessage.Type.BINARY) {
                        try {
                            val buffer = ByteBuffer.allocateDirect(
                                message.payload.readableByteCount()
                            ).apply {
                                message.payload.toByteBuffer(this)
                                flip()
                            }
                            val packet = buffer.parseStreamPacket()

                            if (packet == null) {
                                logger.warn("Failed to parse packet from device $deviceId")
                                return@doOnNext
                            }

                            // Convert PTS from milliseconds to microseconds
                            val ptsMicros = packet.ptsMillis * 1000L
                            val dtsMicros = ptsMicros // Use same value for DTS

                            // Log received packet
                            logger.debug(
                                "Received {} packet from device {}: seq={}, pts={}ms, size={} bytes",
                                packet.type.name,
                                deviceId,
                                packet.sequenceNumber,
                                packet.ptsMillis,
                                packet.payload.size
                            )

                            // Route to appropriate handler
                            when (packet.type) {
                                StreamPacket.PacketType.VIDEO -> {
                                    deviceStreamManager.feedVideoFrame(
                                        deviceId = deviceId,
                                        jpegData = packet.payload,
                                        pts = ptsMicros,
                                        dts = dtsMicros,
                                        sequenceNumber = packet.sequenceNumber
                                    )
                                }
                                StreamPacket.PacketType.AUDIO -> {
                                    deviceStreamManager.feedAudioFrame(
                                        deviceId = deviceId,
                                        aacData = packet.payload,
                                        pts = ptsMicros,
                                        dts = dtsMicros,
                                        sequenceNumber = packet.sequenceNumber
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

