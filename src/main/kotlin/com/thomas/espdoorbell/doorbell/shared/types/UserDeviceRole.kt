package com.thomas.espdoorbell.doorbell.shared.types

enum class UserDeviceRole {
    OWNER,
    MEMBER;

    fun toDisplayName(): String = when (this) {
        OWNER -> "Owner"
        MEMBER -> "Member"
    }

    /** Map device role to Spring Security authority */
    fun toSpringAuthority(): String = when (this) {
        OWNER -> "ROLE_ADMIN"
        MEMBER -> "ROLE_USER"
    }
}