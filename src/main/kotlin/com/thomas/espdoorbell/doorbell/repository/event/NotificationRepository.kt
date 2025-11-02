package com.thomas.espdoorbell.doorbell.repository.event

import com.thomas.espdoorbell.doorbell.model.entity.events.Notifications
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NotificationRepository : CoroutineCrudRepository<Notifications, UUID> {
    @Query("SELECT * FROM notifications WHERE event_id = :eventId")
    fun findAllByEventId(@Param("eventId") eventId: UUID): Flow<Notifications>

    @Query("SELECT * FROM notifications WHERE event_id = ANY (:eventId)")
    fun findAllByEventIds(@Param("eventId") eventId: List<UUID>): Flow<Notifications>
}
