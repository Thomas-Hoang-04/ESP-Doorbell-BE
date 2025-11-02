package com.thomas.espdoorbell.doorbell.repository.event

import com.thomas.espdoorbell.doorbell.model.entity.events.Events
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventRepository : CoroutineCrudRepository<Events, UUID> {
    @Query("SELECT * FROM events WHERE device_id = :deviceId")
    fun findAllByDeviceId(@Param("deviceId") deviceId: UUID): Flow<Events>
}
