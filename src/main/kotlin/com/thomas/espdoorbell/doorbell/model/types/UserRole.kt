package com.thomas.espdoorbell.doorbell.model.types

enum class UserRole {
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