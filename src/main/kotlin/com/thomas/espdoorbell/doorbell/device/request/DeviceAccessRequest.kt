package com.thomas.espdoorbell.doorbell.device.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.thomas.espdoorbell.doorbell.shared.types.UserDeviceRole
import jakarta.validation.constraints.NotNull
import java.util.*

/**
 * Request to grant or update device access for a user.
 */
data class DeviceAccessRequest(
    @field:NotNull(message = "User ID is required")
    @field:JsonProperty("user_id")
    val userId: UUID,

    @field:NotNull(message = "Role is required")
    @field:JsonProperty("role")
    val role: UserDeviceRole = UserDeviceRole.MEMBER
)
