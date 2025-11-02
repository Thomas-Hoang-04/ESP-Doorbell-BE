package com.thomas.espdoorbell.doorbell.repository.user

import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserCredentialRepository : CoroutineCrudRepository<UserCredentials, UUID> {
    @Query("SELECT * FROM user_credentials WHERE username = :username")
    suspend fun findByUsername(username: String): Flow<UserCredentials>

    @Query("SELECT * FROM user_credentials WHERE oauth_provider_id = :oauthProviderId")
    suspend fun findByOauthProviderId(oauthProviderId: String): Flow<UserCredentials>
}
