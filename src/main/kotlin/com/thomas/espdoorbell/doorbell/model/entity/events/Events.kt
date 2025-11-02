package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.dto.event.EventDto
import com.thomas.espdoorbell.doorbell.model.entity.device.Devices
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.entity.user.UserProfiles
import com.thomas.espdoorbell.doorbell.model.types.EventType
import com.thomas.espdoorbell.doorbell.model.types.ResponseType
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "events")
class Events(
    @Column("device_id")
    private val deviceId: UUID,

    @Column("event_timestamp")
    private val eventTimestamp: OffsetDateTime = OffsetDateTime.now(),

    @Column("event_type")
    private val eventType: EventType = EventType.DOORBELL_RING,

    @Column("response_type")
    private val responseType: ResponseType = ResponseType.PENDING,

    @Column("response_timestamp")
    private val responseTimestamp: OffsetDateTime? = null,

    @Column("responded_by")
    private val _respondedBy: UUID? = null,
): BaseEntity() {

    init { validate() }

    val respondedBy: UUID?
        get() = _respondedBy

    override fun validate() {
        responseTimestamp?.let {
            require(!it.isBefore(eventTimestamp)) {
                "Response timestamp must be on or after the event timestamp"
            }
        }
    }

    fun toDto(
        responder: UserProfiles? = null,
        stream: StreamEvents? = null,
        media: MediaEvents? = null,
        notifications: List<Notifications> = emptyList(),
    ): EventDto = EventDto(
        id = id,
        deviceId = deviceId,
        occurredAt = eventTimestamp,
        eventTypeCode = eventType.name,
        eventTypeLabel = eventType.toDisplayName(),
        responseTypeCode = responseType.name,
        responseTypeLabel = responseType.toDisplayName(),
        responseTimestamp = responseTimestamp,
        responderUserId = _respondedBy,
        responderDisplayName = responder?.displayName,
        stream = stream?.toDto(),
        media = media?.toDto(),
        notifications = notifications.map { it.toDto() }
    )
}