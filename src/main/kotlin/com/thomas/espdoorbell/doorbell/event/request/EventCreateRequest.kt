package com.thomas.espdoorbell.doorbell.event.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.thomas.espdoorbell.doorbell.shared.types.EventType
import jakarta.validation.constraints.NotNull
import java.util.*

/**
 * Request DTO for creating a new event
 */
data class EventCreateRequest(
    @field:NotNull(message = "Device ID is required")
    @field:JsonProperty("device_id")
    val deviceId: UUID,

    @field:JsonProperty("event_type")
    val eventType: EventType = EventType.DOORBELL_RING
)
