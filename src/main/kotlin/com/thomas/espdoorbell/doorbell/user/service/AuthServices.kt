package com.thomas.espdoorbell.doorbell.user.service

import com.thomas.espdoorbell.doorbell.user.entity.Users
import com.thomas.espdoorbell.doorbell.user.repository.UserRepository
import com.thomas.espdoorbell.doorbell.user.repository.UserDeviceAccessRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.mono
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class AuthServices(
    private val userRepo: UserRepository,
    private val accessRepo: UserDeviceAccessRepository
): ReactiveUserDetailsService {
    override fun findByUsername(username: String?): Mono<UserDetails?> = mono {
        if (username == null) return@mono null
        val user: Users = userRepo.findByLogin(username) ?: return@mono null
        val deviceAccess = accessRepo.findAllByUser(user.id!!).toList()
        user.toDeviceAccessPrincipal(deviceAccess)
    }
}