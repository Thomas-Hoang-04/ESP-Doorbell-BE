package com.thomas.espdoorbell.doorbell.streaming.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.thomas.espdoorbell.doorbell.streaming.model.SegmentData
import com.thomas.espdoorbell.doorbell.streaming.service.DeviceStreamManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asPublisher
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.util.*

/**
 * WebSocket handler for outbound Android client connections
 * Sends WebM segments with JSON metadata to clients
 */
@Component
class OutboundStreamHandler(
    private val deviceStreamManager: DeviceStreamManager,
    private val objectMapper: ObjectMapper
) : WebSocketHandler {

    private val logger = LoggerFactory.getLogger(OutboundStreamHandler::class.java)

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

        logger.info("Outbound connection established for device $deviceId, session ${session.id}")

        // TODO: Validate user access via UserDeviceAccessRepository
        // For now, allow all connections

        // Check if a pipeline exists
        if (!deviceStreamManager.hasPipeline(deviceId)) {
            logger.warn("No active pipeline for device $deviceId")
            return session.close()
        }

        // Register outbound connection
        deviceStreamManager.registerOutbound(deviceId, session.id)

        val dataBufferFactory = session.bufferFactory()

        // Create a message flow using Kotlin Flow
        val messageFlow = flow {
            // First, send buffered segments for catch-up
            emitAll(sendBufferedSegments(deviceId, dataBufferFactory))
            
            // Then, subscribe to new segments
            emitAll(subscribeToNewSegments(deviceId, dataBufferFactory))
        }
            .catch { error ->
                logger.error("Error in outbound stream for device $deviceId, session ${session.id}", error)
            }
            .onCompletion {
                logger.info("Outbound connection closed for device $deviceId, session ${session.id}")
                // Unregister outbound connection
                try {
                    deviceStreamManager.unregisterOutbound(deviceId, session.id)
                } catch (e: Exception) {
                    logger.error("Error unregistering outbound for device $deviceId", e)
                }
            }

        // Send messages to the client (convert Flow to Publisher for WebFlux)
        return session.send(messageFlow.asPublisher())
            .onErrorResume { error ->
                logger.error("Error in outbound handler for device $deviceId, session ${session.id}", error)
                session.close()
            }
    }

    private fun sendBufferedSegments(
        deviceId: UUID,
        dataBufferFactory: DataBufferFactory
    ): Flow<WebSocketMessage> = flow {
        val bufferedSegments = deviceStreamManager.getBufferedSegments(deviceId)
        
        if (bufferedSegments.isEmpty()) {
            logger.info("No buffered segments for device $deviceId")
            return@flow
        }

        logger.info("Sending ${bufferedSegments.size} buffered segments to device $deviceId")

        bufferedSegments.forEach { segment ->
            emitAll(createSegmentMessages(segment, dataBufferFactory))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun subscribeToNewSegments(
        deviceId: UUID,
        dataBufferFactory: DataBufferFactory
    ): Flow<WebSocketMessage> {
        val segmentFlow = deviceStreamManager.subscribeToSegments(deviceId)
            ?: return emptyFlow()

        logger.info("Subscribed to new segments for device $deviceId")

        return segmentFlow
            .flatMapConcat { segment ->
                logger.debug("Sending segment {} to device {}", segment.index, deviceId)
                createSegmentMessages(segment, dataBufferFactory)
            }
    }

    private fun createSegmentMessages(
        segment: SegmentData,
        dataBufferFactory: DataBufferFactory
    ): Flow<WebSocketMessage> = flow {
        try {
            // Create JSON metadata message
            val metadata = segment.toMetadata()
            val metadataJson = objectMapper.writeValueAsString(metadata)
            val textMessage = WebSocketMessage(
                WebSocketMessage.Type.TEXT,
                dataBufferFactory.wrap(metadataJson.toByteArray())
            )

            // Create a binary WebM message
            val binaryMessage = WebSocketMessage(
                WebSocketMessage.Type.BINARY,
                dataBufferFactory.wrap(segment.data)
            )

            // Send metadata first, then binary data
            emit(textMessage)
            emit(binaryMessage)
        } catch (e: Exception) {
            logger.error("Error creating segment messages", e)
        }
    }
}

