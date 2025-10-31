package com.thomas.espdoorbell.doorbell.model.dto.user

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserDeviceAccessDto(
    val userId: UUID,
    val deviceId: UUID,
    val roleCode: String,
    val roleLabel: String,
    val accessStatusCode: String,
    val accessStatusLabel: String,
    val updatedAt: OffsetDateTime,
    val updatedByUserId: UUID?,
    val updatedByUsername: String?,
)
