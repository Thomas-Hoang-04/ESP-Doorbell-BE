package com.thomas.espdoorbell.doorbell.repository.event

import com.thomas.espdoorbell.doorbell.model.entity.events.Events
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EventRepository : JpaRepository<Events, UUID> {
    @Query("SELECT * FROM events WHERE device_id = :deviceId", nativeQuery = true)
    fun findAllByDeviceId(@Param("deviceId") deviceId: UUID): List<Events>
}
