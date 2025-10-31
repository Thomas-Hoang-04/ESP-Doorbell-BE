package com.thomas.espdoorbell.doorbell.model.dto.event

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NotificationDto(
    val id: UUID,
    val eventId: UUID,
    val typeCode: String,
    val typeLabel: String,
    val recipientTarget: String,
    val recipientUserId: UUID?,
    val sentAt: OffsetDateTime?,
    val successful: Boolean,
    val errorMessage: String?,
)
