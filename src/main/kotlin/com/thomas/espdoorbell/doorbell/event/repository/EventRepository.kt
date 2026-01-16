package com.thomas.espdoorbell.doorbell.event.repository

import com.thomas.espdoorbell.doorbell.event.entity.Events
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Repository
interface EventRepository : CoroutineCrudRepository<Events, UUID> {
    fun findAllByDeviceId(deviceId: UUID): Flow<Events>

    fun findAllByEventTimestampBefore(cutoff: OffsetDateTime): Flow<Events>

    fun findByCreatedAtBetween(start: OffsetDateTime, end: OffsetDateTime): Flow<Events>

    @Query("SELECT * FROM events ORDER BY created_at DESC LIMIT :limit")
    fun findRecent(@Param("limit") limit: Int): Flow<Events>

    suspend fun countByDeviceId(deviceId: UUID): Long
}
