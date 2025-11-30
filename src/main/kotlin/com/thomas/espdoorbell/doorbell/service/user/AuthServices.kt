package com.thomas.espdoorbell.doorbell.service.user

import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import com.thomas.espdoorbell.doorbell.repository.user.UserCredentialRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
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
        val user: UserCredentials = userRepo.findByLogin(username).awaitSingleOrNull() ?: return@mono null
        user.toPrincipal()
    }
}