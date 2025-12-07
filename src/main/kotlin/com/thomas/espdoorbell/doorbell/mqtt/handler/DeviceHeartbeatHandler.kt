package com.thomas.espdoorbell.doorbell.mqtt.handler

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.mqtt.model.DeviceHeartbeatMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Handler for processing device heartbeat messages
 * Updates device online status and metrics in the database
 */
@Component
class DeviceHeartbeatHandler(
    private val deviceService: DeviceService
) {
    private val logger = LoggerFactory.getLogger(DeviceHeartbeatHandler::class.java)

    /**
     * Process a heartbeat message from an ESP32 device
     * Handles unified heartbeat (includes status, battery, signal, uptime)
     */
    suspend fun handleHeartbeat(message: DeviceHeartbeatMessage) {
        try {
            val deviceId = UUID.fromString(message.deviceId)

            logger.debug(
                "Processing heartbeat from device {}: battery={}%, signal={}dBm, active={}",
                deviceId,
                message.batteryLevel,
                message.signalStrength,
                message.isActive
            )

            // Update device with all heartbeat data using reflection-based update
            deviceService.updateDeviceFromHeartbeat(
                deviceId = deviceId,
                isActive = message.isActive,
                batteryLevel = message.batteryLevel,
                signalStrength = message.signalStrength
            )

            // Log firmware version if provided
            message.firmwareVersion?.let { version ->
                // Could add a method to update firmware if needed
                logger.debug("Device {} firmware version: {}", deviceId, version)
            }

            logger.info("Heartbeat processed successfully for device $deviceId")
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid device ID in heartbeat message: ${message.deviceId}", e)
        } catch (e: Exception) {
            logger.error("Error processing heartbeat for device ${message.deviceId}", e)
        }
    }
}


