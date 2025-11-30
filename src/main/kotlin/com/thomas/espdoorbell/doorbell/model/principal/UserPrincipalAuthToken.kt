package com.thomas.espdoorbell.doorbell.model.principal

import org.springframework.security.authentication.AbstractAuthenticationToken

class UserPrincipalAuthToken(
    private val principal: UserPrincipal
): AbstractAuthenticationToken(principal.authorities) {

    init { isAuthenticated = true }

    override fun getCredentials(): Any? = null

    override fun getPrincipal(): UserPrincipal = principal
}