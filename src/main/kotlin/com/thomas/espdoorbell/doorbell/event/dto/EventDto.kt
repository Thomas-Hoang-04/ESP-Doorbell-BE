package com.thomas.espdoorbell.doorbell.event.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.*

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
    
    // Stream info (merged)
    val streamStatusCode: String?,
    val streamStatusLabel: String?,
    val streamStartedAt: OffsetDateTime?,
    val streamEndedAt: OffsetDateTime?,
    val durationSeconds: Int?
)

