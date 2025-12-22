package com.thomas.espdoorbell.doorbell.event.entity

import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.shared.validation.Validatable
import com.thomas.espdoorbell.doorbell.shared.types.EventType
import com.thomas.espdoorbell.doorbell.shared.types.ResponseType
import com.thomas.espdoorbell.doorbell.shared.types.StreamStatus
import com.thomas.espdoorbell.doorbell.user.entity.Users
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
    private val eventTimestamp: OffsetDateTime = OffsetDateTime.now(),

    @Column("event_type")
    private val eventType: EventType = EventType.DOORBELL_RING,

    @Column("response_type")
    private val responseType: ResponseType = ResponseType.PENDING,

    @Column("response_timestamp")
    private val responseTimestamp: OffsetDateTime? = null,

    @Column("responded_by")
    private val _respondedBy: UUID? = null,

    // Stream fields (merged from event_streams)
    @Column("stream_status")
    private val streamStatus: StreamStatus = StreamStatus.STREAMING,

    @Column("stream_started_at")
    private val streamStartedAt: OffsetDateTime? = null,

    @Column("stream_ended_at")
    private val streamEndedAt: OffsetDateTime? = null,

    @Column("duration_seconds")
    private val durationSeconds: Int? = null,

    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: OffsetDateTime? = null,

    @CreatedDate
    @Column("created_at")
    val createdAt: OffsetDateTime? = null,
): Validatable {

    init { validate() }

    val respondedBy: UUID?
        get() = _respondedBy

    override fun validate() {
        responseTimestamp?.let {
            require(!it.isBefore(eventTimestamp)) {
                "Response timestamp must be on or after the event timestamp"
            }
        }
        streamEndedAt?.let { end ->
            streamStartedAt?.let { start ->
                require(end.isAfter(start)) {
                    "Stream end timestamp must be after start timestamp"
                }
            }
        }
        durationSeconds?.let {
            require(it >= 0) { "Duration must not be negative" }
        }
    }

    fun toDto(responder: Users? = null): EventDto = EventDto(
        id = id!!,
        deviceId = deviceId,
        occurredAt = eventTimestamp,
        eventTypeCode = eventType.name,
        eventTypeLabel = eventType.toDisplayName(),
        responseTypeCode = responseType.name,
        responseTypeLabel = responseType.toDisplayName(),
        responseTimestamp = responseTimestamp,
        responderUserId = _respondedBy,
        responderDisplayName = responder?.username,
        streamStatusCode = streamStatus.name,
        streamStatusLabel = streamStatus.toDisplayName(),
        streamStartedAt = streamStartedAt,
        streamEndedAt = streamEndedAt,
        durationSeconds = durationSeconds
    )
}