package com.thomas.espdoorbell.doorbell.streaming.api.dto

data class StreamResponse(
    val success: Boolean,
    val message: String,
    val websocketUrl: String? = null,
    val deviceId: String? = null
)