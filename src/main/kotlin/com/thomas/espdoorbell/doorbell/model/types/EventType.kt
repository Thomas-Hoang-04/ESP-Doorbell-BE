package com.thomas.espdoorbell.doorbell.model.types

enum class EventType {
    DOORBELL_RING,
    MOTION_DETECTED,
    LIVE_VIEW,
    SNAPSHOT,

    SYSTEM_CHECK,
    DEVICE_SETTINGS_UPDATE,
    USER_SETTINGS_UPDATE,
    FIRMWARE_UPDATE;

    fun activatesCamera(): Boolean = this == DOORBELL_RING || this == MOTION_DETECTED || this == LIVE_VIEW

    fun producesMedia(): Boolean = activatesCamera() || this == SNAPSHOT

    fun systemEvent(): Boolean = this == SYSTEM_CHECK || this == DEVICE_SETTINGS_UPDATE || this == USER_SETTINGS_UPDATE || this == FIRMWARE_UPDATE
}