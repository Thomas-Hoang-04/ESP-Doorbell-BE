package com.thomas.espdoorbell.doorbell.intercom.dto

data class IntercomStartResponse(
    val success: Boolean,
    val message: String? = null,
    val sessionId: String? = null
)

data class IntercomStopResponse(
    val success: Boolean,
    val message: String? = null
)
