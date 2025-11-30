package com.thomas.espdoorbell.doorbell.model.types

 enum class UserDeviceRole {
    OWNER,
    ADMIN,
    MEMBER,
    GUEST;

    fun toDisplayName(): String = when (this) {
        OWNER -> "Owner"
        ADMIN -> "Administrator"
        MEMBER -> "Member"
        GUEST -> "Guest"
    }
}