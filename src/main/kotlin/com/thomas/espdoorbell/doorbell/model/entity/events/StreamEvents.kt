package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.dto.event.EventStreamDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntityNoAutoId
import com.thomas.espdoorbell.doorbell.model.types.StreamStatus
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.validation.constraints.Min
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.OffsetDateTime

@Entity
@Table(name = "event_streams")
@AttributeOverride(name = "_id", column = Column(name = "event_id", updatable = false))
class StreamEvents(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "event_id", referencedColumnName = "id", nullable = false, updatable = false)
    private val event: Events,

    @Enumerated(value = EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "stream_status", nullable = false)
    private val streamStatus: StreamStatus = StreamStatus.STREAMING,

    @Column(name = "stream_started_at", nullable = false)
    private val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "stream_ended_at")
    private val endedAt: OffsetDateTime? = null,

    @Column(name = "stream_error_message", columnDefinition = "TEXT")
    private val errorMessage: String? = null,

    @Column(name = "stream_retry_count", nullable = false)
    @field:Min(0)
    private val retryCount: Int = 0,

    @Column(name = "hls_playlist_url", length = 500)
    private val hlsPlaylistUrl: String? = null,

    @Column(name = "raw_video_path", length = 500)
    private val rawVideoPath: String? = null,

    @Column(name = "raw_audio_path", length = 500)
    private val rawAudioPath: String? = null,
): BaseEntityNoAutoId() {
    @PrePersist
    @PreUpdate
    fun validateStreamWindow() {
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