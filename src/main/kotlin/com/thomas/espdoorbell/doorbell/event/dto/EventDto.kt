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
    val eventTypeLabel: String
)
