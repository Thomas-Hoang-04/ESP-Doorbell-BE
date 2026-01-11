package com.thomas.espdoorbell.doorbell.mqtt.handler

import com.thomas.espdoorbell.doorbell.device.repository.DeviceRepository
import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.mqtt.model.DeviceHeartbeatMessage
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Component

@Component
class DeviceHeartbeatHandler(
    private val deviceService: DeviceService,
    private val deviceRepository: DeviceRepository,
    private val passwordEncoder: Argon2PasswordEncoder
) {
    private val logger = LoggerFactory.getLogger(DeviceHeartbeatHandler::class.java)

    suspend fun handleHeartbeat(message: DeviceHeartbeatMessage) {
        try {
            val device = deviceRepository.findByDeviceId(message.deviceId)
            if (device == null) {
                logger.warn("Heartbeat from unknown device: ${message.deviceId}")
                return
            }

            if (message.deviceKey == null) {
                logger.warn("Device key missing in message from device: ${message.deviceId}")
                return
            }

            if (!passwordEncoder.matches(message.deviceKey, device.deviceKey)) {
                logger.error("Invalid device key for device: ${message.deviceId}")
                return
            }

            logger.debug(
                "Authenticated heartbeat from device {}: battery={}%, signal={}dBm",
                message.deviceId,
                message.batteryLevel,
                message.signalStrength
            )

            deviceService.updateDeviceFromHeartbeat(
                deviceId = device.id!!,
                isActive = message.isActive,
                batteryLevel = message.batteryLevel,
                signalStrength = message.signalStrength
            )

            logger.info("Heartbeat processed for device ${message.deviceId}")
        } catch (e: Exception) {
            logger.error("Error processing heartbeat for device ${message.deviceId}", e)
        }
    }
}


