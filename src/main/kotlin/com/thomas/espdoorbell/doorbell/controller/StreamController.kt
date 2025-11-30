package com.thomas.espdoorbell.doorbell.controller

import com.thomas.espdoorbell.doorbell.mqtt.service.MqttPublisherService
import com.thomas.espdoorbell.doorbell.repository.user.UserDeviceAccessRepository
import com.thomas.espdoorbell.doorbell.service.device.DeviceService
import kotlinx.coroutines.flow.firstOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.UUID

// TODO: Rewrite this controller

/**
 * REST controller for handling stream requests from Android clients
 */
@RestController
@RequestMapping("/api/stream")
class StreamController(
    private val mqttPublisherService: MqttPublisherService,
    private val deviceService: DeviceService,
    private val userDeviceAccessRepository: UserDeviceAccessRepository
) {
    private val logger = LoggerFactory.getLogger(StreamController::class.java)

    /**
     * Request to start streaming from an ESP32 device
     * POST /api/stream/request
     */
    @PostMapping("/request")
    suspend fun requestStream(
        @RequestBody request: StreamRequest,
        @RequestHeader("X-User-Id") userId: String // TODO: Get from auth context
    ): ResponseEntity<StreamResponse> {
        try {
            val deviceId = UUID.fromString(request.deviceId)
            val userUuid = UUID.fromString(userId)

            logger.info("Stream request from user $userUuid for device $deviceId")

            // Validate user has access to the device
            val access = userDeviceAccessRepository.findByUserIdAndDeviceId(userUuid, deviceId)
                .firstOrNull()
            
            if (access == null) {
                logger.warn("User $userUuid does not have access to device $deviceId")
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StreamResponse(
                        success = false,
                        message = "You do not have access to this device"
                    ))
            }

            // Check if device exists and is online
            val device = try {
                deviceService.getDevice(deviceId)
            } catch (e: IllegalArgumentException) {
                logger.warn("Device $deviceId not found")
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(StreamResponse(
                        success = false,
                        message = "Device not found"
                    ))
            }

            // Check device online status (consider online if heartbeat within last 2 minutes)
            val isOnline = device.lastOnlineAt?.let { lastOnline ->
                val twoMinutesAgo = OffsetDateTime.now().minusMinutes(2)
                lastOnline.isAfter(twoMinutesAgo)
            } ?: false

            if (!isOnline) {
                logger.warn("Device $deviceId is offline")
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(StreamResponse(
                        success = false,
                        message = "Device is offline"
                    ))
            }

            // Publish MQTT message to trigger streaming
            val published = mqttPublisherService.publishStreamStart(deviceId, userUuid)

            if (!published) {
                logger.error("Failed to publish stream start message for device $deviceId")
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StreamResponse(
                        success = false,
                        message = "Failed to send streaming request to device"
                    ))
            }

            // Return success with WebSocket URL
            val websocketUrl = "ws://localhost:8080/ws/stream/outbound/$deviceId" // TODO: Use actual host
            logger.info("Stream request successful for device $deviceId")

            return ResponseEntity.ok(StreamResponse(
                success = true,
                message = "Stream request sent successfully",
                websocketUrl = websocketUrl,
                deviceId = deviceId.toString()
            ))

        } catch (e: IllegalArgumentException) {
            logger.error("Invalid UUID in stream request", e)
            return ResponseEntity.badRequest()
                .body(StreamResponse(
                    success = false,
                    message = "Invalid device ID or user ID format"
                ))
        } catch (e: Exception) {
            logger.error("Error processing stream request", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(StreamResponse(
                    success = false,
                    message = "Internal server error"
                ))
        }
    }

    /**
     * Request to stop streaming from an ESP32 device
     * POST /api/stream/stop
     */
    @PostMapping("/stop")
    suspend fun stopStream(
        @RequestBody request: StreamRequest,
        @RequestHeader("X-User-Id") userId: String
    ): ResponseEntity<StreamResponse> {
        try {
            val deviceId = UUID.fromString(request.deviceId)
            val userUuid = UUID.fromString(userId)

            logger.info("Stream stop request from user $userUuid for device $deviceId")

            // Validate user has access
            val access = userDeviceAccessRepository.findByUserIdAndDeviceId(userUuid, deviceId)
                .firstOrNull()
            
            if (access == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(StreamResponse(
                        success = false,
                        message = "You do not have access to this device"
                    ))
            }

            // Publish stop message
            val published = mqttPublisherService.publishStreamStop(deviceId)

            if (!published) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(StreamResponse(
                        success = false,
                        message = "Failed to send stop request to device"
                    ))
            }

            return ResponseEntity.ok(StreamResponse(
                success = true,
                message = "Stream stop request sent successfully",
                deviceId = deviceId.toString()
            ))

        } catch (e: Exception) {
            logger.error("Error processing stream stop request", e)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(StreamResponse(
                    success = false,
                    message = "Internal server error"
                ))
        }
    }

    data class StreamRequest(
        val deviceId: String
    )

    data class StreamResponse(
        val success: Boolean,
        val message: String,
        val websocketUrl: String? = null,
        val deviceId: String? = null
    )
}

