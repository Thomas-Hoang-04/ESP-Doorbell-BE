package com.thomas.espdoorbell.doorbell.repository.user

import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import com.thomas.espdoorbell.doorbell.model.entity.user.UserProfiles
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
interface UserCredentialRepository : CoroutineCrudRepository<UserCredentials, UUID> {
    @Query("SELECT * FROM user_credentials WHERE username = :username")
    suspend fun findByUsername(username: String): Mono<UserCredentials>

    @Query("SELECT * FROM user_credentials WHERE email = :email")
    suspend fun findByEmail(@Param("email") email: String): Mono<UserCredentials>

    @Query("SELECT * FROM user_credentials WHERE username = :login OR email = :login")
    suspend fun findByLogin(@Param("login") login: String): Mono<UserCredentials>
}
