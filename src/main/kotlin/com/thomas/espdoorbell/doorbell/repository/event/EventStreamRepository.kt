package com.thomas.espdoorbell.doorbell.repository.event

import com.thomas.espdoorbell.doorbell.model.entity.events.StreamEvents
import com.thomas.espdoorbell.doorbell.model.types.StreamStatus
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventStreamRepository : CoroutineCrudRepository<StreamEvents, UUID> {
    @Query("SELECT * FROM event_streams WHERE stream_status = :status")
    fun findAllByStreamStatus(@Param("status") status: StreamStatus): Flow<StreamEvents>
}
