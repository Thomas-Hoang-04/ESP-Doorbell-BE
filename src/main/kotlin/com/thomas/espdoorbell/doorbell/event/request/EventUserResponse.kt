package com.thomas.espdoorbell.doorbell.event.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.thomas.espdoorbell.doorbell.shared.types.ResponseType
import java.util.*

data class EventUserResponse(
    @field:JsonProperty("user_id")
    val userID: UUID,

    @field:JsonProperty("event_id")
    val eventID: UUID,

    @field:JsonProperty("response")
    val responseType: ResponseType,
)