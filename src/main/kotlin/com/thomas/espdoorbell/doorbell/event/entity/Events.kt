package com.thomas.espdoorbell.doorbell.event.entity

import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.shared.entity.BaseEntity
import com.thomas.espdoorbell.doorbell.shared.types.EventType
import com.thomas.espdoorbell.doorbell.shared.types.ResponseType
import com.thomas.espdoorbell.doorbell.shared.types.StreamStatus
import com.thomas.espdoorbell.doorbell.user.entity.UserProfiles
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

@Table(name = "events")
class Events(
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
    private val durationSeconds: Int? = null
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

    fun toDto(responder: UserProfiles? = null): EventDto = EventDto(
        id = id,
        deviceId = deviceId,
        occurredAt = eventTimestamp,
        eventTypeCode = eventType.name,
        eventTypeLabel = eventType.toDisplayName(),
        responseTypeCode = responseType.name,
        responseTypeLabel = responseType.toDisplayName(),
        responseTimestamp = responseTimestamp,
        responderUserId = _respondedBy,
        responderDisplayName = responder?.fullName,
        streamStatusCode = streamStatus.name,
        streamStatusLabel = streamStatus.toDisplayName(),
        streamStartedAt = streamStartedAt,
        streamEndedAt = streamEndedAt,
        durationSeconds = durationSeconds
    )
}