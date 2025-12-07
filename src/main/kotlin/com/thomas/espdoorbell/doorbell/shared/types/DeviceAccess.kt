package com.thomas.espdoorbell.doorbell.shared.types

enum class DeviceAccess {
    GRANTED,
    REVOKED,
    EXPIRED;

    fun toDisplayName(): String = when (this) {
        GRANTED -> "Access Granted"
        REVOKED -> "Access Revoked"
        EXPIRED -> "Access Expired"
    }
}