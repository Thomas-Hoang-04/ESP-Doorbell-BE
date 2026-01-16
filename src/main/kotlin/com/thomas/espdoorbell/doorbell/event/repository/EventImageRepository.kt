package com.thomas.espdoorbell.doorbell.event.repository

import com.thomas.espdoorbell.doorbell.event.entity.EventImage
import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EventImageRepository : CoroutineCrudRepository<EventImage, UUID> {
    fun findAllByEventId(eventId: UUID): Flow<EventImage>
}
