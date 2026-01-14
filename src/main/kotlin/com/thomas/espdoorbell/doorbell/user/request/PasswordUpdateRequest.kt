package com.thomas.espdoorbell.doorbell.user.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


data class PasswordUpdateRequest(
    @field:NotBlank(message = "Current password is required")
    @field:JsonProperty("old_password")
    val oldPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    @field:JsonProperty("new_password")
    val newPassword: String
)
