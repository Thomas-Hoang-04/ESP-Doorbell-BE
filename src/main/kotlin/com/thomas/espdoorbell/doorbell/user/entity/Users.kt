package com.thomas.espdoorbell.doorbell.user.entity

import com.thomas.espdoorbell.doorbell.shared.validation.Validatable
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.user.dto.UserDto
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "users")
class Users(
    @Id
    @Column("id")
    val id: UUID? = null,

    @Column("username")
    val username: String? = null,

    @Column("email")
    val email: String,

    @Column("password")
    val pwd: String,

    @Column("is_active")
    private val isActive: Boolean = true,

    @Column("is_email_verified")
    private val isEmailVerified: Boolean = false,

    @Column("last_login")
    private val lastLogin: OffsetDateTime? = null,

    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: OffsetDateTime? = null,

    @CreatedDate
    @Column("created_at")
    val createdAt: OffsetDateTime? = null,
): Validatable {

    init { validate() }

    override fun validate() {
        username?.let {
            require(it.matches("^[A-Za-z0-9._-]{3,50}$".toRegex())) {
                "Username must be between 3 and 50 characters (alphanumeric plus dot, underscore, dash)"
            }
        }
        require(pwd.isNotBlank()) { "Password hash must not be blank" }
        require(email.isNotBlank()) { "Email must not be blank" }
        require(email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$".toRegex())) {
            "Email must be in a valid format"
        }
    }

    fun toDto(
        deviceAccess: List<UserDeviceAccess> = emptyList()
    ): UserDto = UserDto(
        id = id!!,
        username = username,
        email = email,
        isActive = isActive,
        isEmailVerified = isEmailVerified,
        lastLoginAt = lastLogin,
        deviceAccess = deviceAccess.map { it.toDto(username) }
    )

    fun isEmailVerified(): Boolean = isEmailVerified

    fun toDeviceAccessPrincipal(
        deviceAccess: List<UserDeviceAccess> = emptyList()
    ): UserPrincipal {
        val authorities = if (deviceAccess.isNotEmpty()) {
            deviceAccess.map { SimpleGrantedAuthority(it.springAuthority) }.distinct()
        } else {
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        }

        return UserPrincipal(
            _id = id!!,
            username = username ?: email,
            password = pwd,
            auth = authorities
        )
    }

    fun toPrincipal(
        authClaims: List<SimpleGrantedAuthority>
    ): UserPrincipal = UserPrincipal(
        _id = id!!,
        username = username ?: email,
        password = pwd,
        auth = authClaims.ifEmpty { listOf(SimpleGrantedAuthority("ROLE_USER")) }
    )
}