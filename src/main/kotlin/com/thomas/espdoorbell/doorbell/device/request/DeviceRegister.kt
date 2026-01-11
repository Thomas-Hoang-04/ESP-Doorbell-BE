package com.thomas.espdoorbell.doorbell.device.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.thomas.espdoorbell.doorbell.device.entity.Devices
import jakarta.validation.constraints.NotBlank

data class DeviceRegister(
    @field:NotBlank(message = "Device ID can not be blank")
    @field:JsonProperty("device_id")
    val deviceID: String,

    @field:NotBlank(message = "Display name can not be blank")
    @field:JsonProperty("display_name")
    val displayName: String,

    @field:JsonProperty("location")
    val locationDescription: String? = null,

    @field:JsonProperty("model")
    val modelName: String? = null,

    @field:JsonProperty("fw_ver")
    val firmwareVersion: String? = null,

    @field:NotBlank(message = "Device key can not be blank")
    @field:JsonProperty("device_key")
    val deviceKey: String,
) {
    fun toEntity(hashedKey: String): Devices = Devices(
        deviceId = deviceID,
        deviceKey = hashedKey,
        name = displayName,
        location = locationDescription,
        model = modelName,
        fwVersion = firmwareVersion,
    )
}