package com.thomas.espdoorbell.doorbell.event.entity

import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.shared.types.EventType
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

@Table(name = "events")
class Events(
    @Id
    @Column("id")
    val id: UUID? = null,

    @Column("device_id")
    val deviceId: UUID,

    @Column("event_timestamp")
    val eventTimestamp: OffsetDateTime = OffsetDateTime.now(),

    @Column("event_type")
    val eventType: EventType = EventType.DOORBELL_RING,

    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: OffsetDateTime? = null,

    @CreatedDate
    @Column("created_at")
    val createdAt: OffsetDateTime? = null
) {
    fun toDto(): EventDto = EventDto(
        id = id!!,
        deviceId = deviceId,
        occurredAt = eventTimestamp,
        eventTypeCode = eventType.name,
        eventTypeLabel = eventType.toDisplayName()
    )
}