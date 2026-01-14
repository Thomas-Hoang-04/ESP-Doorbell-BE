package com.thomas.espdoorbell.doorbell.device.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.*

data class DeviceDto(
    val id: UUID,
    @field:JsonProperty("device_id")
    val deviceIdentifier: String,
    @field:JsonProperty("display_name")
    val displayName: String,
    @field:JsonProperty("location")
    val locationDescription: String?,
    @field:JsonProperty("model")
    val modelName: String?,
    @field:JsonProperty("fw_ver")
    val firmwareVersion: String?,
    val active: Boolean,
    @field:JsonProperty("battery_level")
    val batteryLevelPercent: Int,
    @field:JsonProperty("signal_strength")
    val signalStrengthDbm: Int?,
    @field:JsonProperty("chime_index")
    val chimeIndex: Int,
    @field:JsonProperty("volume_level")
    val volumeLevel: Int,
    @field:JsonProperty("last_online")
    val lastOnlineAt: OffsetDateTime?,
)
