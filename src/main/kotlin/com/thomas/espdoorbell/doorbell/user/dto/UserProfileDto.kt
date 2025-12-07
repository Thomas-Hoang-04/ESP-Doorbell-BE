package com.thomas.espdoorbell.doorbell.user.dto

import java.util.UUID

data class UserProfileDto(
    val id: UUID,
    val fullName: String,
    val phoneNumber: String,
    val notificationEnabled: Boolean
)

