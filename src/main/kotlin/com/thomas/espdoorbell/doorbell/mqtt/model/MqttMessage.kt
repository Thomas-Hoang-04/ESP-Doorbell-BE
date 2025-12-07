package com.thomas.espdoorbell.doorbell.mqtt.model

import com.fasterxml.jackson.annotation.JsonProperty

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
 * Unified heartbeat message from ESP32 (merged heartbeat + status)
 * Topic: doorbell/{deviceId}/heartbeat
 * 
 * Contains device health information published periodically by ESP32.
 */
data class DeviceHeartbeatMessage(
    @field:JsonProperty("device_id")
    override val deviceId: String,
    
    override val timestamp: Long,
    
    @field:JsonProperty("battery_level")
    val batteryLevel: Int?,
    
    @field:JsonProperty("signal_strength")
    val signalStrength: Int?,
    
    val uptime: Long? = null,
    
    @field:JsonProperty("fw_ver")
    val firmwareVersion: String? = null,
    
    @field:JsonProperty("is_active")
    val isActive: Boolean = true
) : MqttMessage()


