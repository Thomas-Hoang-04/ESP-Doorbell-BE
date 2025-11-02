package com.thomas.espdoorbell.doorbell.model.request

import com.fasterxml.jackson.annotation.JsonProperty

data class StreamRegister(
    @field:JsonProperty("stream_retry")
    val retryCount: Int = 0,

    @field:JsonProperty("stream_url")
    val streamUrl: String? = null,
)
