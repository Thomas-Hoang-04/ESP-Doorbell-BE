package com.thomas.espdoorbell.doorbell.device.request

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetTime


data class DeviceUpdateRequest(
    @field:JsonProperty("display_name")
    val displayName: String? = null,

    @field:JsonProperty("location")
    val locationDescription: String? = null,

    @field:JsonProperty("model")
    val modelName: String? = null,

    @field:JsonProperty("fw_ver")
    val firmwareVersion: String? = null,

    @field:JsonProperty("is_active")
    val isActive: Boolean? = null,

    @field:JsonProperty("chime_index")
    val chimeIndex: Int? = null,

    @field:JsonProperty("volume_level")
    val volumeLevel: Int? = null,
    @field:JsonProperty("night_mode_enabled")
    val nightModeEnabled: Boolean? = null,

    @field:JsonFormat(pattern = "HH:mmXXX")
    @field:JsonProperty("night_mode_start")
    val nightModeStart: OffsetTime? = null,

    @field:JsonFormat(pattern = "HH:mmXXX")
    @field:JsonProperty("night_mode_end")
    val nightModeEnd: OffsetTime? = null
)
