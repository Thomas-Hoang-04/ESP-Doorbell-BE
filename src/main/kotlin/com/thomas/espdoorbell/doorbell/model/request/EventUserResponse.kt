package com.thomas.espdoorbell.doorbell.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.thomas.espdoorbell.doorbell.model.types.ResponseType
import java.util.UUID

data class EventUserResponse(
    @field:JsonProperty("user_id")
    val userID: UUID,

    @field:JsonProperty("event_id")
    val eventID: UUID,

    @field:JsonProperty("response")
    val responseType: ResponseType,
)