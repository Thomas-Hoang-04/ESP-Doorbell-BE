package com.thomas.espdoorbell.doorbell.model.dto.user

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate
import java.time.OffsetTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserProfileDto(
    val id: UUID,
    val fullName: String,
    val phoneNumber: String,
    val dateOfBirth: LocalDate?,
    val timezone: String,
    val notificationEnabled: Boolean,
    val quietHoursStart: OffsetTime?,
    val quietHoursEnd: OffsetTime?,
)
