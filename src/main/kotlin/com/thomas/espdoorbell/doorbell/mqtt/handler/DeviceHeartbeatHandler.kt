package com.thomas.espdoorbell.doorbell.mqtt.handler

import com.thomas.espdoorbell.doorbell.mqtt.model.HeartbeatMessage
import com.thomas.espdoorbell.doorbell.service.device.DeviceService
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
     */
    suspend fun handleHeartbeat(message: HeartbeatMessage) {
        try {
            val deviceId = UUID.fromString(message.deviceId)

            logger.debug(
                "Processing heartbeat from device {}: battery={}%, signal={}dBm",
                deviceId,
                message.batteryLevel,
                message.signalStrength
            )

            // Update device online status
            deviceService.updateDeviceOnlineStatus(deviceId, isOnline = true)

            // Update device metrics
            deviceService.updateDeviceMetrics(
                deviceId = deviceId,
                batteryLevel = message.batteryLevel,
                signalStrength = message.signalStrength
            )

            // Optionally update the firmware if provided
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

