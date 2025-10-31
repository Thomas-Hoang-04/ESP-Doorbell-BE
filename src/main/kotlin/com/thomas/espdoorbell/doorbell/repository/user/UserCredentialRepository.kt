package com.thomas.espdoorbell.doorbell.repository.user

import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserCredentialRepository : JpaRepository<UserCredentials, UUID> {
    fun findByUsername(username: String): Optional<UserCredentials>
    fun findByOauthProviderId(oauthProviderId: String): Optional<UserCredentials>
}
