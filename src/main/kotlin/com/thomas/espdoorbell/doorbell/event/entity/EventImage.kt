package com.thomas.espdoorbell.doorbell.event.entity

import com.thomas.espdoorbell.doorbell.event.dto.EventImageDto
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

@Table(name = "event_images")
class EventImage(
    @Id
    @Column("id")
    val id: UUID? = null,

    @Column("event_id")
    val eventId: UUID,

    @Column("file_path")
    val filePath: String,

    @Column("captured_at")
    val capturedAt: OffsetDateTime = OffsetDateTime.now(),

    @CreatedDate
    @Column("created_at")
    val createdAt: OffsetDateTime? = null
) {
    fun toDto(baseUrl: String): EventImageDto = EventImageDto(
        id = id!!,
        imageUrl = "$baseUrl/$filePath",
        capturedAt = capturedAt
    )
}
