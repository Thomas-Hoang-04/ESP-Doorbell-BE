package com.thomas.espdoorbell.doorbell.user.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserCredentialDto(
    val id: UUID,
    val email: String,
    val username: String?,
    val isActive: Boolean,
    val isEmailVerified: Boolean,
    val lastLoginAt: OffsetDateTime?,
    val profile: UserProfileDto?,
    val deviceAccess: List<UserDeviceAccessDto>,
)
