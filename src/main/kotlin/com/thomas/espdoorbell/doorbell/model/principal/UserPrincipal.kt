package com.thomas.espdoorbell.doorbell.model.principal

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

class UserPrincipal(
    private val _id: UUID,
    private val username: String,
    private val auth: Collection<SimpleGrantedAuthority>,
    private val password: String
): UserDetails {

    val id: UUID
        get() = _id

    override fun getAuthorities(): Collection<SimpleGrantedAuthority> = auth

    override fun getPassword(): String = password

    override fun getUsername(): String = username
}