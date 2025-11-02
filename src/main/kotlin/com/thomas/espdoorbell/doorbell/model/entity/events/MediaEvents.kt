package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.dto.event.EventMediaDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.UUID

@Table(name = "event_media")
class MediaEvents(
    @Transient
    private val event: UUID,

    @Column("video_url")
    private val videoUrl: String? = null,

    @Column("thumbnail_url")
    private val thumbnailUrl: String? = null,

    @Column("duration_seconds")
    private val durationSeconds: Int? = null,

    @Column("video_codec")
    private val videoCodec: String? = null,

    @Column("audio_codec")
    private val audioCodec: String? = null,

    @Column("resolution")
    private val resolution: String? = null,

    @Column("file_size_bytes")
    private val fileSizeBytes: Long? = null,
): BaseEntity(id = event) {

    init { validate() }

    override fun validate() {
        durationSeconds?.let { require(it >= 0) { "Duration seconds must not be negative" } }
        fileSizeBytes?.let { require(it >= 0) { "File size must not be negative" } }
        videoUrl?.let { require(it.length <= 500) { "Video URL exceeds maximum length" } }
        thumbnailUrl?.let { require(it.length <= 500) { "Thumbnail URL exceeds maximum length" } }
        resolution?.let { require(it.length <= 20) { "Resolution exceeds maximum length" } }
        videoCodec?.let { require(it.length <= 50) { "Video codec exceeds maximum length" } }
        audioCodec?.let { require(it.length <= 50) { "Audio codec exceeds maximum length" } }
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
