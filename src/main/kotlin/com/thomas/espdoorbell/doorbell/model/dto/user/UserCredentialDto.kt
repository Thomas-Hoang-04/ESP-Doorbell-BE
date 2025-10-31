package com.thomas.espdoorbell.doorbell.model.dto.user

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserCredentialDto(
    val id: UUID,
    val authProviderCode: String,
    val authProviderLabel: String,
    val username: String?,
    val oauthProviderId: String?,
    val isActive: Boolean,
    val isEmailVerified: Boolean,
    val lastLoginAt: OffsetDateTime?,
    val profile: UserProfileDto?,
    val deviceAccess: List<UserDeviceAccessDto>,
)
