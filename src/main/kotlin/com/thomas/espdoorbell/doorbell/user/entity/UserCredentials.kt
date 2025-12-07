package com.thomas.espdoorbell.doorbell.user.entity

import com.thomas.espdoorbell.doorbell.shared.entity.BaseEntity
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.user.dto.UserCredentialDto
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.OffsetDateTime

@Table(name = "user_credentials")
class UserCredentials(
    @Column("username")
    private val username: String? = null,

    @Column("email")
    private val rawEmail: String,

    @Column("password")
    private val passwordHash: String,

    @Column("is_active")
    private val isActive: Boolean = true,

    @Column("is_email_verified")
    private val isEmailVerified: Boolean = false,

    @Column("last_login")
    private val lastLogin: OffsetDateTime? = null,
): BaseEntity() {

    val email: String
        get() = rawEmail.lowercase()

    init { validate() }

    override fun validate() {
        username?.let {
            require(it.matches("^[A-Za-z0-9._-]{3,50}$".toRegex())) {
                "Username must be between 3 and 50 characters (alphanumeric plus dot, underscore, dash)"
            }
        }
        require(passwordHash.isNotBlank()) { "Password hash must not be blank" }
        require(email.isNotBlank()) { "Email must not be blank" }
        require(email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$".toRegex())) {
            "Email must be in a valid format"
        }
    }

    fun toDto(
        profile: UserProfiles,
        deviceAccess: List<UserDeviceAccess> = emptyList()
    ): UserCredentialDto = UserCredentialDto(
        id = id,
        username = username,
        email = email,
        isActive = isActive,
        isEmailVerified = isEmailVerified,
        lastLoginAt = lastLogin,
        profile = profile.toDto(),
        deviceAccess = deviceAccess.map { it.toDto() }
    )

    fun toPrincipal(
        authClaims: List<SimpleGrantedAuthority>
            = listOf(SimpleGrantedAuthority("ROLE_USER"))
    ): UserPrincipal
        = UserPrincipal(
            _id = id,
            username = username ?: email,
            password = passwordHash,
            auth = authClaims
        )
}