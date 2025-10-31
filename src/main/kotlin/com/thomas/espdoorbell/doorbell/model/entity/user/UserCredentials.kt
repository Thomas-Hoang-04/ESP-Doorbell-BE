package com.thomas.espdoorbell.doorbell.model.entity.user

import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.entity.events.Events
import com.thomas.espdoorbell.doorbell.model.types.AuthProvider
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
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

    @OneToOne(mappedBy = "cred", cascade = [CascadeType.ALL], optional = true)
    private val profile: UserProfiles
): BaseEntity() {
    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    private val deviceAccessAssignments: MutableSet<UserDeviceAccess> = mutableSetOf()

    @OneToMany(mappedBy = "updatedBy", fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    private val accessAuditTrail: MutableList<UserDeviceAccess> = mutableListOf()

    @OneToMany(mappedBy = "respondedBy", fetch = FetchType.LAZY,
        cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    private val eventResponses: MutableList<Events> = mutableListOf()

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