package com.thomas.espdoorbell.doorbell.shared.types

enum class StreamStatus {
    STREAMING,
    PROCESSING,
    COMPLETED,
    FAILED;

    fun toDisplayName(): String = when (this) {
        STREAMING -> "Streaming"
        PROCESSING -> "Processing"
        COMPLETED -> "Completed"
        FAILED -> "Failed"
    }
}