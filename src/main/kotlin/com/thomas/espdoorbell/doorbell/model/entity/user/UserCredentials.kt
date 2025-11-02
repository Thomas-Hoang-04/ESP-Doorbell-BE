package com.thomas.espdoorbell.doorbell.model.entity.user

import com.thomas.espdoorbell.doorbell.model.dto.user.UserCredentialDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.types.AuthProvider
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

@Table(name = "user_credentials")
class UserCredentials(
    @Column("auth_provider")
    private val authProvider: AuthProvider = AuthProvider.LOCAL,

    @Column("username")
    private val username: String? = null,

    @Column("pwd")
    private val pwd: String? = null,

    @Column("oauth_provider_id")
    private val oauthProviderId: String? = null,

    @Column("is_active")
    private val isActive: Boolean = true,

    @Column("is_email_verified")
    private val isEmailVerified: Boolean = false,

    @Column("last_login")
    private val lastLogin: OffsetDateTime? = null,
): BaseEntity() {

    init { validate() }

    override fun validate() {
        when (authProvider) {
            AuthProvider.LOCAL -> {
                require(oauthProviderId.isNullOrBlank()) { "OAuth Provider ID must not be provided when using LOCAL auth" }
                require(!username.isNullOrBlank()) { "Username must be provided when using LOCAL auth" }
                require(!pwd.isNullOrBlank()) { "Password must be provided when using LOCAL auth" }
                require(username.length <= 100) { "Username exceeds maximum length" }
            }
            else -> {
                require(!oauthProviderId.isNullOrBlank()) { "OAuth Provider ID must exist when using OAuth" }
                require(pwd.isNullOrBlank()) { "Password must not be provided when using OAuth" }
            }
        }
    }

    fun toDto(
        profile: UserProfiles,
        deviceAccess: List<UserDeviceAccess> = emptyList()
    ): UserCredentialDto = UserCredentialDto(
        id = id,
        authProviderCode = authProvider.name,
        authProviderLabel = authProvider.toDisplayName(),
        username = username,
        oauthProviderId = oauthProviderId,
        isActive = isActive,
        isEmailVerified = isEmailVerified,
        lastLoginAt = lastLogin,
        profile = profile.toDto(),
        deviceAccess = deviceAccess.map { it.toDto() }
    )
}