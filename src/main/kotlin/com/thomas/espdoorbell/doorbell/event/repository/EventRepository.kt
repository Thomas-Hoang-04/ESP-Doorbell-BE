package com.thomas.espdoorbell.doorbell.event.repository

import com.thomas.espdoorbell.doorbell.event.entity.Events
import com.thomas.espdoorbell.doorbell.shared.types.StreamStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Repository
interface EventRepository : CoroutineCrudRepository<Events, UUID> {
    
    @Query("SELECT * FROM events WHERE device_id = :deviceId")
    fun findAllByDeviceId(@Param("deviceId") deviceId: UUID): Flow<Events>

    @Query("SELECT * FROM events WHERE created_at BETWEEN :start AND :end")
    fun findByDateRange(
        @Param("start") start: OffsetDateTime,
        @Param("end") end: OffsetDateTime
    ): Flow<Events>

    @Query("SELECT * FROM events ORDER BY created_at DESC LIMIT :limit")
    fun findRecent(@Param("limit") limit: Int): Flow<Events>

    @Query("SELECT COUNT(*) FROM events WHERE device_id = :deviceId")
    suspend fun countByDeviceId(@Param("deviceId") deviceId: UUID): Long

    @Query("SELECT * FROM events WHERE stream_status = :status")
    fun findByStreamStatus(@Param("status") status: StreamStatus): Flow<Events>
}


