package com.thomas.espdoorbell.doorbell.user.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

/**
 * Request DTO for updating user profile
 * Only non-null fields will be updated
 */
data class ProfileUpdateRequest(
    @field:JsonProperty("display_name")
    val displayName: String? = null,

    @field:Email(message = "Email must be valid")
    @field:JsonProperty("email")
    val email: String? = null,

    @field:Size(min = 8, message = "Password must be at least 8 characters")
    @field:JsonProperty("password")
    val newPassword: String? = null,

    @field:JsonProperty("phone")
    val phoneNumber: String? = null
)
