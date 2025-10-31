package com.thomas.espdoorbell.doorbell.model.dto.event

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.OffsetDateTime
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EventStreamDto(
    val eventId: UUID,
    val statusCode: String,
    val statusLabel: String,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val errorMessage: String?,
    val retryCount: Int,
    val hlsPlaylistUrl: String?,
    val rawVideoPath: String?,
    val rawAudioPath: String?,
)
