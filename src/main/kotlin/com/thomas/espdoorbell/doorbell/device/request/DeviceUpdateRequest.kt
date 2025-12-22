package com.thomas.espdoorbell.doorbell.device.request

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request DTO for updating device properties
 * Only non-null fields will be updated
 */
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
    val isActive: Boolean? = null
)
