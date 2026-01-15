package com.thomas.espdoorbell.doorbell.streaming.websocket.handler

import com.thomas.espdoorbell.doorbell.core.jwt.JWTManager
import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.pipeline.DeviceStreamManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.reactive.asPublisher
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import org.springframework.core.io.buffer.DataBufferFactory
import java.util.UUID

@Component
class OutboundStreamHandler(
    private val deviceStreamManager: DeviceStreamManager,
    private val jwtManager: JWTManager,
    private val deviceService: DeviceService
) : WebSocketHandler {

    private val logger = LoggerFactory.getLogger(OutboundStreamHandler::class.java)

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

        logger.info("Outbound connection established for device $deviceId, session ${session.id}")

        return mono {
            val token = extractToken(session)
            if (token == null) {
                logger.warn("Missing authorization token for outbound stream")
                throw SecurityException("Missing authorization token")
            }

            val decodedJwt = try {
                jwtManager.decode(token)
            } catch (e: Exception) {
                logger.warn("Invalid JWT token for outbound stream: ${e.message}")
                throw SecurityException("Invalid authorization token")
            }

            val userId = try {
                UUID.fromString(decodedJwt.subject)
            } catch (_: Exception) {
                logger.warn("Invalid user ID in JWT: ${decodedJwt.subject}")
                throw SecurityException("Invalid authorization token")
            }

            val hasAccess = deviceService.hasAccess(deviceId, userId)
            if (!hasAccess) {
                logger.warn("User $userId does not have access to device $deviceId")
                throw SecurityException("Access denied")
            }

            logger.info("User $userId authenticated for device $deviceId stream")
            userId
        }.flatMap { _ ->
            mono {
                if (!deviceStreamManager.hasPipeline(deviceId)) {
                    logger.info("Waiting for pipeline for device $deviceId...")
                    val pipelineAvailable = deviceStreamManager.waitForPipeline(deviceId, 30000)
                    if (!pipelineAvailable) {
                        logger.warn("Timeout waiting for pipeline for device $deviceId")
                        throw IllegalStateException("Stream not available - device may be offline")
                    }
                    logger.info("Pipeline became available for device $deviceId")
                }
                Unit
            }.flatMap {
                deviceStreamManager.registerOutbound(deviceId, session.id)

                val dataBufferFactory = session.bufferFactory()

                val messageFlow = createMessageFlow(deviceId, dataBufferFactory)
                    .onCompletion {
                        logger.info("Outbound connection closed for device $deviceId, session ${session.id}")
                        try {
                            deviceStreamManager.unregisterOutbound(deviceId, session.id)
                        } catch (e: Exception) {
                            logger.error("Error unregistering outbound for device $deviceId", e)
                        }
                    }

                session.send(messageFlow.asPublisher())
                    .onErrorResume { error ->
                        logger.error("Error in outbound handler for device $deviceId, session ${session.id}", error)
                        session.close()
                    }
            }
        }.onErrorResume { error ->
            logger.error("Authentication failed for outbound stream", error)
            session.close()
        }
    }

    private fun extractToken(session: WebSocketSession): String? {
        val authHeader = session.handshakeInfo.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7)
        }
        return session.handshakeInfo.uri.query
            ?.split("&")
            ?.map { it.split("=") }
            ?.find { it.size == 2 && it[0] == "token" }
            ?.get(1)
    }

    private fun createMessageFlow(
        deviceId: UUID,
        dataBufferFactory: DataBufferFactory
    ): Flow<WebSocketMessage> = flow {
        val initSegment = deviceStreamManager.getInitSegment(deviceId)
        if (initSegment != null) {
            logger.info("Sending init segment ({} bytes) to new client for device {}", initSegment.size, deviceId)
            emit(WebSocketMessage(WebSocketMessage.Type.BINARY, dataBufferFactory.wrap(initSegment)))
        } else {
            logger.warn("No init segment available for device {}", deviceId)
        }

        val clusterFlow = deviceStreamManager.subscribeToClusterFlow(deviceId)
        if (clusterFlow == null) {
            logger.warn("No cluster flow available for device {}", deviceId)
            return@flow
        }

        logger.info("Subscribed to cluster flow for device {}", deviceId)

        clusterFlow.collect { clusterData ->
            logger.debug("Sending cluster ({} bytes) to device {}", clusterData.size, deviceId)
            emit(WebSocketMessage(WebSocketMessage.Type.BINARY, dataBufferFactory.wrap(clusterData)))
        }
    }
}
