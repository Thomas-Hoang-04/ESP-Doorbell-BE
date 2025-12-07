package com.thomas.espdoorbell.doorbell.event.request

import com.fasterxml.jackson.annotation.JsonProperty

data class StreamRegister(
    @field:JsonProperty("stream_retry")
    val retryCount: Int = 0,

    @field:JsonProperty("stream_url")
    val streamUrl: String? = null,
)
