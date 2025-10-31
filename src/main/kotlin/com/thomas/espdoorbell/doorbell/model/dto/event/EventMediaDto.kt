package com.thomas.espdoorbell.doorbell.model.dto.event

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class EventMediaDto(
    val eventId: UUID,
    val videoUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Int?,
    val videoCodec: String?,
    val audioCodec: String?,
    val resolution: String?,
    val fileSizeBytes: Long?,
)
