package com.thomas.espdoorbell.doorbell.model.dto.user

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserProfileDto(
    val id: UUID,
    val displayName: String,
    val emailAddress: String?,
    val phoneNumber: String?,
    val notificationsEnabled: Boolean,
    val quietHoursStart: OffsetTime?,
    val quietHoursEnd: OffsetTime?,
)
