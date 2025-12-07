package com.thomas.espdoorbell.doorbell.user.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Request DTO for user registration
 */
data class UserRegisterRequest(
    @field:Pattern(
        regexp = "^[A-Za-z0-9._-]{3,50}$",
        message = "Username must be 3-50 alphanumeric characters (plus ._-)"
    )
    @field:JsonProperty("username")
    val username: String? = null,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    @field:JsonProperty("email")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    @field:JsonProperty("password")
    val password: String,

    @field:NotBlank(message = "Display name is required")
    @field:JsonProperty("display_name")
    val displayName: String,

    @field:JsonProperty("phone")
    val phoneNumber: String? = null
)
