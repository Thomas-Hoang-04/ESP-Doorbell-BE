package com.thomas.espdoorbell.doorbell.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.UUID

data class UserDto(
    val id: UUID,
    val email: String,
    val username: String?,
    @field:JsonProperty("active")
    val isActive: Boolean,
    @field:JsonProperty("email_verified")
    val isEmailVerified: Boolean,
    @field:JsonProperty("last_login")
    val lastLoginAt: OffsetDateTime?,
    @field:JsonProperty("device_access")
    val deviceAccess: List<UserDeviceAccessDto>,
)
