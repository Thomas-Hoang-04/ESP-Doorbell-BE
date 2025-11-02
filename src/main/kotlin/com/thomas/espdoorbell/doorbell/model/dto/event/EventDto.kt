package com.thomas.espdoorbell.doorbell.model.dto.event

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EventDto(
    val id: UUID,
    val deviceId: UUID,
    val occurredAt: OffsetDateTime,
    val eventTypeCode: String,
    val eventTypeLabel: String,
    val responseTypeCode: String,
    val responseTypeLabel: String,
    val responseTimestamp: OffsetDateTime?,
    val responderUserId: UUID?,
    val responderDisplayName: String?,
    val stream: EventStreamDto?,
    val media: EventMediaDto?,
    val notifications: List<NotificationDto>,
)
