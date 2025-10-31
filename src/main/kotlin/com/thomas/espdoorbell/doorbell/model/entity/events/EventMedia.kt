package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.dto.event.EventMediaDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntityNoAutoId
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.validation.constraints.Min

@Entity
@Table(name = "event_media")
@AttributeOverride(name = "_id", column = Column(name = "event_id", updatable = false))
class EventMedia(
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "event_id", referencedColumnName = "id", nullable = false, updatable = false)
    private val event: Events,

    @Column(name = "video_url", length = 500)
    private val videoUrl: String? = null,

    @Column(name = "thumbnail_url", length = 500)
    private val thumbnailUrl: String? = null,

    @Column(name = "duration_seconds")
    @field:Min(0)
    private val durationSeconds: Int? = null,

    @Column(name = "video_codec", length = 50)
    private val videoCodec: String? = null,

    @Column(name = "audio_codec", length = 50)
    private val audioCodec: String? = null,

    @Column(name = "resolution", length = 20)
    private val resolution: String? = null,

    @Column(name = "file_size_bytes")
    @field:Min(0)
    private val fileSizeBytes: Long? = null,
): BaseEntityNoAutoId() {
    @PrePersist
    @PreUpdate
    fun validateMedia() {
        videoUrl?.let { require(it.length <= 500) { "Video URL exceeds maximum length" } }
        thumbnailUrl?.let { require(it.length <= 500) { "Thumbnail URL exceeds maximum length" } }
    }

    fun toDto(): EventMediaDto = EventMediaDto(
        eventId = id,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        resolution = resolution,
        fileSizeBytes = fileSizeBytes,
    )
}
