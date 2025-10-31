package com.thomas.espdoorbell.doorbell.repository.event

import com.thomas.espdoorbell.doorbell.model.entity.events.StreamEvents
import com.thomas.espdoorbell.doorbell.model.types.StreamStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventStreamRepository : JpaRepository<StreamEvents, UUID> {
    @Query("select * from event_streams where stream_status = :status::stream_status_enum", nativeQuery = true)
    fun findAllByStreamStatus(@Param("status") status: StreamStatus): List<StreamEvents>
}
