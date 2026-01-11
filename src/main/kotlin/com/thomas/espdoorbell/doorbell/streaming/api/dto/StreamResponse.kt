package com.thomas.espdoorbell.doorbell.streaming.api.dto

data class StreamResponse(
    val success: Boolean,
    val message: String? = null,
    val websocketUrl: String? = null
)