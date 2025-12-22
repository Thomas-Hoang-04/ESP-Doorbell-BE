package com.thomas.espdoorbell.doorbell.user.controller

import com.thomas.espdoorbell.doorbell.core.jwt.JWTManager
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.user.dto.AvailabilityResponse
import com.thomas.espdoorbell.doorbell.user.dto.LoginResponse
import com.thomas.espdoorbell.doorbell.user.dto.UserDto
import com.thomas.espdoorbell.doorbell.user.request.LoginRequest
import com.thomas.espdoorbell.doorbell.user.request.UserRegisterRequest
import com.thomas.espdoorbell.doorbell.user.service.UserService
import jakarta.validation.Valid
import kotlinx.coroutines.reactor.awaitSingleOrNull
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

    /**
     * Authenticate a user and return a JWT token.
     */
    @PostMapping("/login")
    suspend fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val authToken = UsernamePasswordAuthenticationToken(request.username, request.password)
        val authentication = authenticationManager.authenticate(authToken).awaitSingleOrNull()
            ?: throw BadCredentialsException("Invalid password")

        val principal = authentication.principal as UserPrincipal

        // Generate JWT with roles
        val roles = principal.authorities.map { it.authority }
        val token = jwtManager.issue(principal.id, principal.username, roles)

        // Get full user details
        val user = userService.getUser(principal.id)

        return ResponseEntity.ok(LoginResponse(token, user))
    }

    /**
     * Register a new user account.
     */
    @PostMapping("/register")
    suspend fun register(
        @Valid @RequestBody request: UserRegisterRequest
    ): ResponseEntity<UserDto> {
        val user = userService.registerUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    /**
     * Check if a username is available.
     */
    @GetMapping("/check-username")
    suspend fun checkUsernameAvailability(
        @RequestParam username: String
    ): AvailabilityResponse =
        AvailabilityResponse(userService.isUsernameAvailable(username))

    /**
     * Check if an email is available.
     */
    @GetMapping("/check-email")
    suspend fun checkEmailAvailability(
        @RequestParam email: String
    ): AvailabilityResponse =
        AvailabilityResponse(userService.isEmailAvailable(email))

    @GetMapping("/check-exist")
    suspend fun checkLoginAvailability(
        @RequestParam login: String
    ): AvailabilityResponse =
        AvailabilityResponse(userService.isLoginAvailable(login))
}
