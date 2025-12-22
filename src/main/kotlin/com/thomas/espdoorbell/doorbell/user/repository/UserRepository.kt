package com.thomas.espdoorbell.doorbell.user.repository

import com.thomas.espdoorbell.doorbell.user.entity.Users
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : CoroutineCrudRepository<Users, UUID> {
    suspend fun findByUsername(username: String): Users?
    suspend fun existsByUsername(username: String): Boolean

    suspend fun findByEmailIgnoreCase(email: String): Users?
    suspend fun existsByEmail(email: String): Boolean

    @Query("SELECT * FROM users WHERE username = :login OR email = LOWER(:login)")
    suspend fun findByLogin(@Param("login") login: String): Users?

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE username = :login OR email = LOWER(:login))")
    suspend fun existsByLogin(@Param("login") login: String): Boolean
}


