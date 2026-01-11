package com.thomas.espdoorbell.doorbell.streaming.api

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.mqtt.service.MqttPublisherService
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.streaming.api.dto.StreamResponse
import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/stream")
class StreamController(
    private val mqttPublisherService: MqttPublisherService,
    private val deviceService: DeviceService,
    private val streamingProperties: StreamingProperties
) {
    private val logger = LoggerFactory.getLogger(StreamController::class.java)

    @PostMapping("/{deviceId}/start")
    suspend fun startStream(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<StreamResponse> {
        logger.info("Stream start request from user {} for device {}", principal.id, deviceId)

        if (!deviceService.hasAccess(deviceId, principal.id)) {
            logger.warn("User {} does not have access to device {}", principal.id, deviceId)
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(StreamResponse(success = false, message = "No access to device"))
        }

        val device = try {
            deviceService.getDevice(deviceId)
        } catch (_: Exception) {
            logger.warn("Device {} not found", deviceId)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(StreamResponse(success = false, message = "Device not found"))
        }

        val isOnline = device.lastOnlineAt?.isAfter(OffsetDateTime.now().minusMinutes(2)) ?: false

        if (!isOnline) {
            logger.warn("Device {} is offline", deviceId)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(StreamResponse(success = false, message = "Device offline"))
        }

        val published = mqttPublisherService.publishStreamStart(deviceId, principal.id)
        if (!published) {
            logger.error("Failed to publish stream start for device {}", deviceId)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(StreamResponse(success = false, message = "Failed to send command"))
        }

        val websocketUrl = "${streamingProperties.websocketBaseUrl}/ws/stream/outbound/$deviceId"
        logger.info("Stream start successful for device {}", deviceId)

        return ResponseEntity.ok(StreamResponse(
            success = true,
            websocketUrl = websocketUrl
        ))
    }

    @PostMapping("/{deviceId}/stop")
    suspend fun stopStream(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<StreamResponse> {
        logger.info("Stream stop request from user {} for device {}", principal.id, deviceId)

        if (!deviceService.hasAccess(deviceId, principal.id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(StreamResponse(success = false, message = "No access to device"))
        }

        val published = mqttPublisherService.publishStreamStop(deviceId)
        if (!published) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(StreamResponse(success = false, message = "Failed to send command"))
        }

        return ResponseEntity.ok(StreamResponse(success = true))
    }
}
