package com.thomas.espdoorbell.doorbell.user.repository

import com.thomas.espdoorbell.doorbell.user.entity.UserCredentials
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserCredentialRepository : CoroutineCrudRepository<UserCredentials, UUID> {
    
    @Query("SELECT * FROM user_credentials WHERE username = :username")
    suspend fun findByUsername(@Param("username") username: String): UserCredentials?

    @Query("SELECT * FROM user_credentials WHERE email = :email")
    suspend fun findByEmail(@Param("email") email: String): UserCredentials?

    @Query("SELECT * FROM user_credentials WHERE username = :login OR email = :login")
    suspend fun findByLogin(@Param("login") login: String): UserCredentials?

    @Query("SELECT EXISTS(SELECT 1 FROM user_credentials WHERE username = :username)")
    suspend fun existsByUsername(@Param("username") username: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM user_credentials WHERE email = :email)")
    suspend fun existsByEmail(@Param("email") email: String): Boolean
}


