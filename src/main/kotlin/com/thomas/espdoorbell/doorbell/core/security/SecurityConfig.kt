package com.thomas.espdoorbell.doorbell.core.security

import com.thomas.espdoorbell.doorbell.core.jwt.JWTAuthFilter
import com.thomas.espdoorbell.doorbell.user.service.AuthServices
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtAuthFilter: JWTAuthFilter,
    private val authServices: AuthServices,
    private val passwordEncoder: PasswordEncoder,
) {

    @Bean
    fun authenticationManager(): ReactiveAuthenticationManager =
        UserDetailsRepositoryReactiveAuthenticationManager(
            authServices
        ).apply {
            setPasswordEncoder(passwordEncoder)
        }

    @Bean
    fun securityWebFilterChain(
        http: ServerHttpSecurity,
        authenticationManager: ReactiveAuthenticationManager
    ): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .logout { it.disable() }
        .authenticationManager(authenticationManager)
        .authorizeExchange {
            it.pathMatchers(
                "/actuator/health",
                "/actuator/info",
                "/api/auth/**",
                "/api/verify/**",
                "/api/events/bell-ring",
                "/ws/stream/**"
            ).permitAll()
            it.anyExchange().authenticated()
        }
        .addFilterAt(jwtAuthFilter, SecurityWebFiltersOrder.AUTHENTICATION)
        .build()
}