package com.thomas.espdoorbell.doorbell.config.jwt

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import com.thomas.espdoorbell.doorbell.model.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.repository.user.UserCredentialRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.Optional

@Component
class JWTToPrincipal(
    private val userRepo: UserCredentialRepository
) {
    private fun extractAuthClaim(jwt: DecodedJWT)
        : List<SimpleGrantedAuthority> {
        val claim: Claim = jwt.getClaim("auth")
        if (claim.isNull || claim.isMissing) return emptyList()
        return claim.asList(SimpleGrantedAuthority::class.java)
    }

    suspend fun convert(jwt: DecodedJWT): Optional<UserPrincipal> {
        val username = jwt.getClaim("username").asString() ?: return Optional.empty()
        val user: UserCredentials = userRepo.findByLogin(username).awaitSingleOrNull() ?: return Optional.empty()
        assert(user.id == UUID.fromString(jwt.subject))
        return Optional.of(user.toPrincipal(extractAuthClaim(jwt)))
    }
}