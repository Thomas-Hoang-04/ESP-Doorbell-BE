package com.thomas.espdoorbell.doorbell.user.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

/**
 * Request DTO for user login.
 */
data class LoginRequest(
    @field:NotBlank(message = "Username is required")
    @field:JsonProperty("username")
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:JsonProperty("password")
    val password: String
)
