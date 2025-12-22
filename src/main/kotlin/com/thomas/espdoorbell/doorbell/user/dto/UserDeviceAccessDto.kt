package com.thomas.espdoorbell.doorbell.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.UUID

data class UserDeviceAccessDto(
    @field:JsonProperty("user_id")
    val userId: UUID,
    @field:JsonProperty("device_id")
    val deviceId: UUID,
    @field:JsonProperty("role")
    val roleCode: String,
    @field:JsonProperty("role_label")
    val roleLabel: String,
    @field:JsonProperty("access_status")
    val accessStatusCode: String,
    @field:JsonProperty("access_status_label")
    val accessStatusLabel: String,
    @field:JsonProperty("updated_at")
    val updatedAt: OffsetDateTime,
    @field:JsonProperty("updated_by")
    val updatedByUserId: UUID?,
    @field:JsonProperty("updated_by_name")
    val updatedByUsername: String?,
)
