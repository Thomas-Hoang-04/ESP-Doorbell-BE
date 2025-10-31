package com.thomas.espdoorbell.doorbell.model.types

enum class AuthProvider {
    LOCAL,
    OAUTH_GOOGLE,
    OAUTH_APPLE;

    fun toDisplayName(): String = when (this) {
        LOCAL -> "Local Account"
        OAUTH_GOOGLE -> "Google OAuth"
        OAUTH_APPLE -> "Apple OAuth"
    }
}