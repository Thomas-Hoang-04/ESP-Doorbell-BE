package com.thomas.espdoorbell.doorbell.event.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime
import java.util.UUID

data class EventImageDto(
    val id: UUID,
    @field:JsonProperty("image_url")
    val imageUrl: String,
    @field:JsonProperty("captured_at")
    val capturedAt: OffsetDateTime
)
