package com.thomas.espdoorbell.doorbell.config.jwt

import com.thomas.espdoorbell.doorbell.model.principal.UserPrincipalAuthToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.withContext
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange

@Component
class JWTAuthFilter(
    private val jwtManager: JWTManager,
    private val jwtToPrincipal: JWTToPrincipal
): CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain
    ) {
        val token = exchange.extractToken() ?: return chain.filter(exchange)

        val decodedJwt = jwtManager.decode(token)

        val principal = jwtToPrincipal.convert(decodedJwt)
            .orElseThrow{ BadCredentialsException("Invalid JWT token") }

        val auth = UserPrincipalAuthToken(principal)

        val context = ReactiveSecurityContextHolder.withAuthentication(auth)

        withContext(ReactorContext(context)) {
            chain.filter(exchange)
        }
    }

    private fun ServerWebExchange.extractToken(): String? {
        val authHeader = this.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        return if (authHeader != null && authHeader.startsWith("Bearer "))
            authHeader.substring(7)
        else null
    }

}