package com.thomas.espdoorbell.doorbell.user.controller

import com.thomas.espdoorbell.doorbell.core.jwt.JWTManager
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.user.dto.AvailabilityResponse
import com.thomas.espdoorbell.doorbell.user.dto.LoginResponse
import com.thomas.espdoorbell.doorbell.user.dto.UserDto
import com.thomas.espdoorbell.doorbell.user.request.LoginRequest
import com.thomas.espdoorbell.doorbell.user.request.PasswordResetRequest
import com.thomas.espdoorbell.doorbell.user.request.UserRegisterRequest
import com.thomas.espdoorbell.doorbell.user.service.UserService
import jakarta.validation.Valid
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val userService: UserService,
    private val jwtManager: JWTManager,
    private val authenticationManager: ReactiveAuthenticationManager
) {
    private val logger = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    suspend fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val authToken = UsernamePasswordAuthenticationToken(request.username, request.password)
        val authentication = authenticationManager.authenticate(authToken).awaitSingleOrNull()
            ?: run {
                logger.warn("Login failed for user: ${request.username}")
                throw BadCredentialsException("Invalid password")
            }

        val principal = authentication.principal as UserPrincipal

        val roles = principal.authorities.map { it.authority }
        val token = jwtManager.issue(principal.id, principal.username, roles)

        val user = userService.getUser(principal.id)
        userService.updateLoginTimestamp(user.id)

        logger.info("User logged in: ${user.id}")
        return ResponseEntity.ok(LoginResponse(token, user))
    }

    @PostMapping("/register")
    suspend fun register(
        @Valid @RequestBody request: UserRegisterRequest
    ): ResponseEntity<UserDto> =
        userService.registerUser(request).let {
            logger.info("User registered: ${it.id}")
            ResponseEntity.status(HttpStatus.CREATED).body(it)
        }

    @GetMapping("/check-username")
    suspend fun checkUsernameAvailability(
        @RequestParam username: String
    ): ResponseEntity<AvailabilityResponse> =
        userService.isUsernameAvailable(username).let {
            ResponseEntity.ok().body(AvailabilityResponse(it))
        }

    @GetMapping("/check-email")
    suspend fun checkEmailAvailability(
        @RequestParam email: String
    ): ResponseEntity<AvailabilityResponse> =
        userService.isEmailAvailable(email).let {
            ResponseEntity.ok().body(AvailabilityResponse(it))
        }

    @GetMapping("/check-exist")
    suspend fun checkLoginExists(
        @RequestParam login: String
    ): ResponseEntity<AvailabilityResponse> =
        userService.isLoginExists(login).let {
            ResponseEntity.ok().body(AvailabilityResponse(it))
        }

    @PostMapping("/reset-password")
    suspend fun resetPassword(
        @Valid @RequestBody request: PasswordResetRequest
    ): ResponseEntity<AvailabilityResponse> =
        userService.resetPassword(request.login, request.newPassword).let {
            logger.info("Password reset for: ${request.login}")
            ResponseEntity.ok().body(AvailabilityResponse(it))
        }
}
