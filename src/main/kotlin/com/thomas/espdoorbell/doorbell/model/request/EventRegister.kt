package com.thomas.espdoorbell.doorbell.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.thomas.espdoorbell.doorbell.model.types.EventType

data class EventRegister(
    @field:JsonProperty("device_id")
    val deviceID: String,

    @field:JsonProperty("event_type")
    val type: EventType,
)
