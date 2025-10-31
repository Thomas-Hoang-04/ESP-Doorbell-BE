package com.thomas.espdoorbell.doorbell.repository.event

import com.thomas.espdoorbell.doorbell.model.entity.events.Notifications
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface NotificationRepository : JpaRepository<Notifications, UUID> {
    @Query("select * from notifications where event_id = :eventId", nativeQuery = true)
    fun findAllByEventId(@Param("eventId") eventId: UUID): List<Notifications>
}
