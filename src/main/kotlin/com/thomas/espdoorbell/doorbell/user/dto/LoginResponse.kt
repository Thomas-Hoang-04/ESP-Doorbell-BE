package com.thomas.espdoorbell.doorbell.user.dto


data class LoginResponse(
    val token: String,
    val user: UserDto
)
