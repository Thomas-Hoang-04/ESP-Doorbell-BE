package com.thomas.espdoorbell.doorbell.user.dto

/**
 * Response DTO for successful login.
 */
data class LoginResponse(
    val token: String,
    val user: UserDto
)
