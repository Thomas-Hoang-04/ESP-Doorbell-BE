package com.thomas.espdoorbell.doorbell.model.types

enum class ResponseType {
    ANSWERED,
    MISSED,
    DECLINED,
    AUTO_RESPONSE,
    SYSTEM_RESPONSE,
    PENDING;

    fun toDisplayName(): String = when (this) {
        ANSWERED -> "Answered"
        MISSED -> "Missed"
        DECLINED -> "Declined"
        AUTO_RESPONSE -> "Auto Responded"
        SYSTEM_RESPONSE -> "System Responded"
        PENDING -> "Awaiting Response"
    }
}