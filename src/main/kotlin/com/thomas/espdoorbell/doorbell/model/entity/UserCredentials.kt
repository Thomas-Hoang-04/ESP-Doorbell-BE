package com.thomas.espdoorbell.doorbell.model.entity

import com.thomas.espdoorbell.doorbell.model.types.AuthProvider
import jakarta.persistence.*
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.OffsetDateTime

@Entity
@Table(name = "user_credentials")
class UserCredentials(
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "auth_provider", nullable = false)
    private val authProvider: AuthProvider = AuthProvider.LOCAL,

    @Column(name = "username", unique = true, length = 100)
    private val username: String? = null,

    @Column(name = "pwd", unique = true)
    private val pwd: String? = null,

    @Column(name = "oauth_provider_id")
    private val oauthProviderId: String? = null,

    @Column(name = "is_active")
    private val isActive: Boolean = true,

    @Column(name = "is_email_verified")
    private val isEmailVerified: Boolean = false,

    @Column(name = "last_login")
    private val lastLogin: OffsetDateTime? = null,
): BaseEntity() {
    @OneToOne(mappedBy = "cred", cascade = [CascadeType.ALL], optional = false)
    private lateinit var profile: UserProfiles

    @OneToOne(mappedBy = "user_id", cascade = [CascadeType.ALL], optional = false)
    private lateinit var access: UserDeviceAccess

    @OneToMany(mappedBy = "granted_by", orphanRemoval = false, fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    private val accessGrantLogs: MutableList<UserDeviceAccess> = mutableListOf()

    @OneToMany(mappedBy = "responded_by", orphanRemoval = false, fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    private val eventResponseLogs: MutableList<Events> = mutableListOf()

    @PrePersist
    @PreUpdate
    fun validateAuthData() {
        when (authProvider) {
            AuthProvider.LOCAL -> {
                require(oauthProviderId.isNullOrBlank()) { "OAuth Provider ID must not be provided when using LOCAL auth" }
                require(!username.isNullOrBlank()) { "Username must be provided when using LOCAL auth" }
                require(!pwd.isNullOrBlank()) { "Password must be provided when using LOCAL auth" }
            }
            else -> {
                require(!oauthProviderId.isNullOrBlank()) { "OAuth Provider ID must exist when using OAuth" }
                require(pwd.isNullOrBlank()) { "Password must not be provided when using OAuth" }
            }
        }
    }
}