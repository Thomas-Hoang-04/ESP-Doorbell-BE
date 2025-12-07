package com.thomas.espdoorbell.doorbell.event.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.thomas.espdoorbell.doorbell.shared.types.EventType

data class EventRegister(
    @field:JsonProperty("device_id")
    val deviceID: String,

    @field:JsonProperty("event_type")
    val type: EventType,
)
