package com.thomas.espdoorbell.doorbell.mqtt.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * Base sealed class for all MQTT message types
 */
sealed class MqttMessage {
    abstract val deviceId: String
    abstract val timestamp: Long
}

/**
 * Message to trigger ESP32 to start streaming
 * Topic: doorbell/{deviceId}/stream/start
 */
data class StreamStartMessage(
    val action: String = "start_stream",
    
    @field:JsonProperty("device_id")
    override val deviceId: String,
    
    override val timestamp: Long = System.currentTimeMillis(),
    
    @field:JsonProperty("requested_by")
    val requestedBy: String,
    
    val quality: String = "high"
) : MqttMessage()

/**
 * Message to stop ESP32 streaming
 * Topic: doorbell/{deviceId}/stream/stop
 */
data class StreamStopMessage(
    val action: String = "stop_stream",

    @field:JsonProperty("device_id")
    override val deviceId: String,
    
    override val timestamp: Long = System.currentTimeMillis()
) : MqttMessage()

/**
 * Heartbeat message from ESP32
 * Topic: doorbell/{deviceId}/heartbeat
 */
data class HeartbeatMessage(
    @field:JsonProperty("device_id")
    override val deviceId: String,
    
    override val timestamp: Long,
    
    @field:JsonProperty("battery_level")
    val batteryLevel: Int,
    
    @field:JsonProperty("signal_strength")
    val signalStrength: Int,
    
    val uptime: Long? = null,
    
    @field:JsonProperty("fw_ver")
    val firmwareVersion: String? = null
) : MqttMessage()

/**
 * Status update message from ESP32
 * Topic: doorbell/{deviceId}/status
 */
data class StatusMessage(
    @field:JsonProperty("device_id")
    override val deviceId: String,
    
    override val timestamp: Long,
    
    @field:JsonProperty("active")
    val isActive: Boolean,

    @field:JsonProperty("fw_ver")
    val firmwareVersion: String,

    @field:JsonProperty("battery_level")
    val batteryLevel: Int? = null,
    
    @field:JsonProperty("signal_strength")
    val signalStrength: Int? = null,
    
    val message: String? = null
) : MqttMessage()

