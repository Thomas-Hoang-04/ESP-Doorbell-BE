package com.thomas.espdoorbell.doorbell.model.types

enum class NotificationMethod {
    PUSH,
    SMS,
    EMAIL;

    fun toDisplayName(): String = when (this) {
        PUSH -> "Push Notification"
        SMS -> "SMS"
        EMAIL -> "Email"
    }
}