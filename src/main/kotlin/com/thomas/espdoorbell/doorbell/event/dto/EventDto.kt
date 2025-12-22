package com.thomas.espdoorbell.doorbell.event.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.*

data class EventDto(
    val id: UUID,
    @field:JsonProperty("device_id")
    val deviceId: UUID,
    @field:JsonProperty("occurred_at")
    val occurredAt: OffsetDateTime,
    @field:JsonProperty("event_code")
    val eventTypeCode: String,
    @field:JsonProperty("event_label")
    val eventTypeLabel: String,
    @field:JsonProperty("response_code")
    val responseTypeCode: String,
    @field:JsonProperty("response_label")
    val responseTypeLabel: String,
    @field:JsonProperty("response_timestamp")
    val responseTimestamp: OffsetDateTime?,
    @field:JsonProperty("responder_user_id")
    val responderUserId: UUID?,
    @field:JsonProperty("responder_display_name")
    val responderDisplayName: String?,

    // Stream info (merged)
    @field:JsonProperty("stream_status_code")
    val streamStatusCode: String?,
    @field:JsonProperty("stream_status_label")
    val streamStatusLabel: String?,
    @field:JsonProperty("stream_started")
    val streamStartedAt: OffsetDateTime?,
    @field:JsonProperty("stream_ended")
    val streamEndedAt: OffsetDateTime?,
    @field:JsonProperty("duration")
    val durationSeconds: Int?
)

