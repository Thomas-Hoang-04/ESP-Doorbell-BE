package com.thomas.espdoorbell.doorbell.streaming.handler

import com.thomas.espdoorbell.doorbell.service.device.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.model.StreamPacket
import com.thomas.espdoorbell.doorbell.streaming.model.parseStreamPacket
import com.thomas.espdoorbell.doorbell.streaming.service.DeviceStreamManager
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.util.*

/**
 * WebSocket handler for inbound ESP32 device connections
 * Receives binary packets with video/audio data and feeds them to the transcoding pipeline
 */
@Component
class InboundStreamHandler(
    private val deviceStreamManager: DeviceStreamManager,
    private val deviceService: DeviceService
) : WebSocketHandler {

    private val logger = LoggerFactory.getLogger(InboundStreamHandler::class.java)

    override fun handle(session: WebSocketSession): Mono<Void> {
        // Extract deviceId from path
        val deviceIdStr = session.handshakeInfo.uri.path.split("/").lastOrNull()
        if (deviceIdStr == null) {
            logger.error("Invalid WebSocket path: ${session.handshakeInfo.uri.path}")
            return session.close()
        }

        val deviceId: UUID
        try {
            deviceId = UUID.fromString(deviceIdStr)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid device ID format: $deviceIdStr")
            return session.close()
        }

        logger.info("Inbound connection established for device $deviceId, session ${session.id}")

        // Validate device exists and register inbound connection
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
                    if (message.type == org.springframework.web.reactive.socket.WebSocketMessage.Type.BINARY) {
                        try {
                            val buffer = message.payload.asByteBuffer()
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
                                "Received ${packet.type.name} packet from device $deviceId: " +
                                        "seq=${packet.sequenceNumber}, pts=${packet.ptsMillis}ms, size=${packet.payload.size} bytes"
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

