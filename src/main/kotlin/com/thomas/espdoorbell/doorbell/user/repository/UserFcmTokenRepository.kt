package com.thomas.espdoorbell.doorbell.user.repository

import com.thomas.espdoorbell.doorbell.user.entity.UserFcmToken
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Repository
interface UserFcmTokenRepository : CoroutineCrudRepository<UserFcmToken, UUID> {
    fun findByUserId(userId: UUID): Flow<UserFcmToken>
    suspend fun findByUserIdAndToken(userId: UUID, token: String): UserFcmToken?
    suspend fun deleteByToken(token: String)

    @Modifying
    @Query("DELETE FROM user_fcm_tokens WHERE user_id IN (SELECT id FROM users WHERE last_login < :threshold)")
    suspend fun deleteByInactiveUsersBefore(@Param("threshold") threshold: OffsetDateTime): Long

    @Query("SELECT * FROM user_fcm_tokens WHERE user_id = ANY (:userIds)")
    fun findByUserIdIn(@Param("userIds") userIds: List<UUID>): Flow<UserFcmToken>
}

