package com.thomas.espdoorbell.doorbell.shared.types

enum class ResponseType {
    ANSWERED,
    MISSED,
    DECLINED,
    PENDING;

    fun toDisplayName(): String = when (this) {
        ANSWERED -> "Answered"
        MISSED -> "Missed"
        DECLINED -> "Declined"
        PENDING -> "Awaiting Response"
    }
}