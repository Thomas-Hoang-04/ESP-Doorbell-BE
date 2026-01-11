package com.thomas.espdoorbell.doorbell.mqtt.model

import com.fasterxml.jackson.annotation.JsonProperty

sealed class MqttMessage {
    abstract val deviceId: String
    abstract val timestamp: Long
}

data class StreamStartMessage(
    val action: String = "start_stream",

    @field:JsonProperty("device_id")
    override val deviceId: String,

    override val timestamp: Long = System.currentTimeMillis(),

    @field:JsonProperty("requested_by")
    val requestedBy: String,

    val quality: String = "high"
) : MqttMessage()

data class StreamStopMessage(
    val action: String = "stop_stream",

    @field:JsonProperty("device_id")
    override val deviceId: String,

    override val timestamp: Long = System.currentTimeMillis()
) : MqttMessage()

data class DeviceHeartbeatMessage(
    @field:JsonProperty("device_id")
    override val deviceId: String,

    override val timestamp: Long,

    @field:JsonProperty("device_key")
    val deviceKey: String? = null,

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

data class BellEventMessage(
    @field:JsonProperty("device_id")
    override val deviceId: String,

    override val timestamp: Long = System.currentTimeMillis(),

    @field:JsonProperty("device_key")
    val deviceKey: String? = null,

    val event: String = "bell_pressed"
) : MqttMessage()


