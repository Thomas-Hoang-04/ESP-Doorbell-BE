package com.thomas.espdoorbell.doorbell.mqtt.handler

import com.thomas.espdoorbell.doorbell.device.repository.DeviceRepository
import com.thomas.espdoorbell.doorbell.event.request.EventCreateRequest
import com.thomas.espdoorbell.doorbell.event.service.EventService
import com.thomas.espdoorbell.doorbell.mqtt.model.BellEventMessage
import com.thomas.espdoorbell.doorbell.shared.types.EventType
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Component

@Component
class BellEventHandler(
    private val deviceRepository: DeviceRepository,
    private val passwordEncoder: Argon2PasswordEncoder,
    private val eventService: EventService
) {
    private val logger = LoggerFactory.getLogger(BellEventHandler::class.java)

    suspend fun handleBellEvent(message: BellEventMessage) {
        try {
            val device = deviceRepository.findByDeviceId(message.deviceId)
            if (device == null) {
                logger.warn("Bell event from unknown device: ${message.deviceId}")
                return
            }

            if (message.deviceKey == null) {
                logger.warn("Device key missing in message from device: ${message.deviceId}")
                return
            }

            if (!passwordEncoder.matches(message.deviceKey, device.deviceKey)) {
                logger.error("Invalid device key for bell event from device: ${message.deviceId}")
                return
            }

            logger.info("Bell pressed on device: ${message.deviceId}")

            eventService.createEvent(
                EventCreateRequest(
                    deviceId = device.id!!,
                    eventType = EventType.DOORBELL_RING
                )
            )
        } catch (e: Exception) {
            logger.error("Error processing bell event for device ${message.deviceId}", e)
        }
    }
}
