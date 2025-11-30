package com.thomas.espdoorbell.doorbell.config.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.thomas.espdoorbell.doorbell.config.security.RSAKeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class JWTManager(
    private val rsa: RSAKeyProperties
) {
    suspend fun issue(id: UUID, username: String, roles: List<String>): String
        = withContext(Dispatchers.Default) {
            JWT.create()
                .withSubject(id.toString())
                .withExpiresAt(Instant.now().plus(Duration.ofDays(1)))
                .withClaim("username", username)
                .withClaim("auth", roles)
                .sign(Algorithm.RSA512(rsa.publicKey, rsa.privateKey))
        }

    suspend fun decode(token: String): DecodedJWT
        = withContext(Dispatchers.Default) {
            JWT.require(
                Algorithm.RSA512(rsa.publicKey, rsa.privateKey)
            ).build().verify(token)
        }
}