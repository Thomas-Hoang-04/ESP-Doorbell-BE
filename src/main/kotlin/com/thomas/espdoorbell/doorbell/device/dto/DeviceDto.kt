package com.thomas.espdoorbell.doorbell.device.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.*

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DeviceDto(
    val id: UUID,
    val deviceIdentifier: String,
    val displayName: String,
    val locationDescription: String?,
    val modelName: String?,
    val firmwareVersion: String?,
    val active: Boolean,
    val batteryLevelPercent: Int,
    val signalStrengthDbm: Int?,
    val lastOnlineAt: OffsetDateTime?,
)
