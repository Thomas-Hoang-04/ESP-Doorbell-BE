package com.thomas.espdoorbell.doorbell.user.service

import com.thomas.espdoorbell.doorbell.user.entity.UserCredentials
import com.thomas.espdoorbell.doorbell.user.repository.UserCredentialRepository
import kotlinx.coroutines.reactor.mono
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class AuthServices(
    private val userRepo: UserCredentialRepository
): ReactiveUserDetailsService {
    override fun findByUsername(username: String?): Mono<UserDetails?> = mono {
        if (username == null) return@mono null
        val user: UserCredentials = userRepo.findByLogin(username) ?: return@mono null
        user.toPrincipal()
    }
}