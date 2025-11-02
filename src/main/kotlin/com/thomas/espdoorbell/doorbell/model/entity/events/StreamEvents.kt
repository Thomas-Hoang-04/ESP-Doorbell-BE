package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.dto.event.EventStreamDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.types.StreamStatus
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "event_streams")
class StreamEvents(
    @Transient
    private val event: UUID,

    @Column("stream_status")
    private val streamStatus: StreamStatus = StreamStatus.STREAMING,

    @Column("stream_started_at")
    @CreatedDate
    private val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("stream_ended_at")
    private val endedAt: OffsetDateTime? = null,

    @Column("stream_error_message")
    private val errorMessage: String? = null,

    @Column("stream_retry_count")
    private val retryCount: Int = 0,

    @Column("hls_playlist_url")
    private val hlsPlaylistUrl: String? = null,

    @Column("raw_video_path")
    private val rawVideoPath: String? = null,

    @Column("raw_audio_path")
    private val rawAudioPath: String? = null,
): BaseEntity(id = event) {

    init { validate() }

    override fun validate() {
        require(retryCount >= 0) { "Retry count must not be negative" }
        hlsPlaylistUrl?.let { require(it.length <= 500) { "HLS playlist URL exceeds maximum length" } }
        rawVideoPath?.let { require(it.length <= 500) { "Raw video path exceeds maximum length" } }
        rawAudioPath?.let { require(it.length <= 500) { "Raw audio path exceeds maximum length" } }
        endedAt?.let {
            require(it.isAfter(startedAt)) {
                "Stream end timestamp must be after start timestamp"
            }
        }
    }

    fun toDto(): EventStreamDto = EventStreamDto(
        eventId = id,
        statusCode = streamStatus.name,
        statusLabel = streamStatus.toDisplayName(),
        startedAt = startedAt,
        endedAt = endedAt,
        errorMessage = errorMessage,
        retryCount = retryCount,
        hlsPlaylistUrl = hlsPlaylistUrl,
        rawVideoPath = rawVideoPath,
        rawAudioPath = rawAudioPath
    )
}