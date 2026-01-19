package com.thomas.espdoorbell.doorbell.intercom

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.intercom.dto.IceAnswerRequest
import com.thomas.espdoorbell.doorbell.intercom.dto.IceConfigResponse
import com.thomas.espdoorbell.doorbell.intercom.dto.IceOfferResponse
import com.thomas.espdoorbell.doorbell.intercom.dto.IntercomStartResponse
import com.thomas.espdoorbell.doorbell.intercom.dto.IntercomStopResponse
import com.thomas.espdoorbell.doorbell.mqtt.service.MqttPublisherService
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/intercom")
class IntercomController(
    private val deviceService: DeviceService,
    private val sessionManager: IntercomSessionManager,
    private val mqttPublisher: MqttPublisherService,
    private val iceProperties: IceProperties
) {
    private val logger = LoggerFactory.getLogger(IntercomController::class.java)

    @PostMapping("/{deviceId}/start")
    suspend fun startIntercom(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<IntercomStartResponse> {
        logger.info("Intercom start request from user {} for device {}", principal.id, deviceId)

        if (!deviceService.hasAccess(deviceId, principal.id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(IntercomStartResponse(success = false, message = "No access to device"))
        }

        val device = try {
            deviceService.getDevice(deviceId)
        } catch (_: Exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(IntercomStartResponse(success = false, message = "Device not found"))
        }

        val isOnline = device.lastOnlineAt?.isAfter(OffsetDateTime.now().minusMinutes(2)) ?: false
        if (!isOnline) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(IntercomStartResponse(success = false, message = "Device offline"))
        }

        if (sessionManager.hasSession(device.deviceIdentifier)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(IntercomStartResponse(success = false, message = "Intercom session already active"))
        }

        sessionManager.createSession(deviceId, device.deviceIdentifier, principal.id)

        val payloadMap = mapOf("userId" to principal.id)
        val payload = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(payloadMap)

        val published = mqttPublisher.publishRaw(
            "doorbell/${device.deviceIdentifier}/intercom/start",
            payload
        )

        if (!published) {
            sessionManager.endSession(device.deviceIdentifier)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(IntercomStartResponse(success = false, message = "Failed to signal device"))
        }

        logger.info("Intercom session started for device {}", deviceId)
        return ResponseEntity.ok(IntercomStartResponse(
            success = true,
            sessionId = device.deviceIdentifier
        ))
    }

    @PostMapping("/{deviceId}/stop")
    suspend fun stopIntercom(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<IntercomStopResponse> {
        logger.info("Intercom stop request from user {} for device {}", principal.id, deviceId)

        if (!deviceService.hasAccess(deviceId, principal.id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(IntercomStopResponse(success = false, message = "No access"))
        }

        val device = try {
            deviceService.getDevice(deviceId)
        } catch (_: Exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(IntercomStopResponse(success = false, message = "Device not found"))
        }

        sessionManager.endSession(device.deviceIdentifier)

        val payloadMap = mapOf("reason" to "user_ended")
        val payload = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().writeValueAsString(payloadMap)

        mqttPublisher.publishRaw(
            "doorbell/${device.deviceIdentifier}/intercom/stop",
            payload
        )

        logger.info("Intercom session stopped for device {}", deviceId)
        return ResponseEntity.ok(IntercomStopResponse(success = true))
    }

    @GetMapping("/{deviceId}/offer")
    suspend fun getEspOffer(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<IceOfferResponse> {
        if (!deviceService.hasAccess(deviceId, principal.id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(IceOfferResponse(success = false, message = "No access"))
        }

        val device = try {
            deviceService.getDevice(deviceId)
        } catch (_: Exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(IceOfferResponse(success = false, message = "Device not found"))
        }

        val session =
            sessionManager.getSession(device.deviceIdentifier)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(IceOfferResponse(success = false, message = "No active session"))

        val sdp = session.espSdp
        val candidates = session.espCandidates

        if (sdp.isNullOrEmpty()) {
            return ResponseEntity.ok(IceOfferResponse(success = false, message = "Waiting for ESP offer"))
        }

        return ResponseEntity.ok(IceOfferResponse(
            success = true,
            sdp = sdp,
            candidates = candidates
        ))
    }

    @PostMapping("/{deviceId}/answer")
    suspend fun sendAnswer(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody answer: IceAnswerRequest
    ): ResponseEntity<Map<String, Any>> {
        if (!deviceService.hasAccess(deviceId, principal.id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mapOf("success" to false, "message" to "No access"))
        }

        val device = try {
            deviceService.getDevice(deviceId)
        } catch (_: Exception) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(mapOf("success" to false, "message" to "Device not found"))
        }

        val payloadMap = mapOf(
            "sdp" to answer.sdp,
            "candidates" to answer.candidates
        )
        val payload = jacksonObjectMapper().writeValueAsString(payloadMap)

        val published = mqttPublisher.publishRaw(
            "doorbell/${device.deviceIdentifier}/intercom/answer",
            payload
        )

        if (!published) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("success" to false, "message" to "Failed to send answer"))
        }

        logger.info("Sent ICE answer to device {}", deviceId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/config")
    fun getIceConfig(): ResponseEntity<IceConfigResponse> {
        return ResponseEntity.ok(IceConfigResponse(
            turnHost = iceProperties.turnHost,
            turnPort = iceProperties.turnPort,
            turnUsername = iceProperties.turnUsername,
            turnPassword = iceProperties.turnPassword
        ))
    }
}

