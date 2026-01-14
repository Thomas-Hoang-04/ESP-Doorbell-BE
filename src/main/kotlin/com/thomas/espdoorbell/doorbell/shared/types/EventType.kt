package com.thomas.espdoorbell.doorbell.shared.types

enum class EventType {
    DOORBELL_RING,
    MOTION_DETECTED;

    fun toDisplayName(): String = when (this) {
        DOORBELL_RING -> "Doorbell Ring"
        MOTION_DETECTED -> "Motion Detected"
    }
}