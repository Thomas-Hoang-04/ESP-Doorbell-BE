package com.thomas.espdoorbell.doorbell.event.service

import com.thomas.espdoorbell.doorbell.core.exception.EventNotFoundException
import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.event.entity.Events
import com.thomas.espdoorbell.doorbell.event.repository.EventRepository
import com.thomas.espdoorbell.doorbell.event.request.EventCreateRequest
import com.thomas.espdoorbell.doorbell.shared.types.StreamStatus
import com.thomas.espdoorbell.doorbell.user.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val userRepository: UserRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate
) {
    // ========== READ ==========

    @Transactional(readOnly = true)
    suspend fun listEvents(): List<EventDto> {
        val events = eventRepository.findAll().toList()
        if (events.isEmpty()) return emptyList()
        return getEventData(events)
    }

    @Transactional(readOnly = true)
    suspend fun listEventsByDevice(deviceId: UUID): List<EventDto> {
        val events = eventRepository.findAllByDeviceId(deviceId).toList()
        if (events.isEmpty()) return emptyList()
        return getEventData(events)
    }

    @Transactional(readOnly = true)
    suspend fun listEventsByDateRange(start: OffsetDateTime, end: OffsetDateTime): List<EventDto> {
        require(!start.isAfter(end)) { "Start date must not be after end date" }
        val events = eventRepository.findByCreatedAtBetween(start, end).toList()
        if (events.isEmpty()) return emptyList()
        return getEventData(events)
    }

    @Transactional(readOnly = true)
    suspend fun listRecentEvents(limit: Int = 10): List<EventDto> {
        require(limit > 0) { "Limit must be positive" }
        val events = eventRepository.findRecent(limit).toList()
        if (events.isEmpty()) return emptyList()
        return getEventData(events)
    }

    fun listActiveStreams(): Flow<EventDto> =
        eventRepository.findByStreamStatus(StreamStatus.STREAMING).map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun getEvent(eventId: UUID): EventDto {
        val event = eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId)
        return getEventData(listOf(event)).first()
    }

    suspend fun getEventCountByDevice(deviceId: UUID): Long =
        eventRepository.countByDeviceId(deviceId)

    // ========== CREATE ==========

    @Transactional
    suspend fun createEvent(request: EventCreateRequest): EventDto {
        val event = Events(
            deviceId = request.deviceId,
            eventType = request.eventType
        )
        val savedEvent = eventRepository.save(event)
        return savedEvent.toDto()
    }

    // ========== UPDATE ==========

    @Transactional
    suspend fun updateStreamStatus(eventId: UUID, status: StreamStatus, endedAt: OffsetDateTime? = null) {
        eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId)

        val query = Query.query(Criteria.where("id").`is`(eventId))
        var update = Update.update("stream_status", status.name)

        endedAt?.let { update = update.set("stream_ended_at", it) }

        r2dbcEntityTemplate.update(query, update, Events::class.java).awaitSingleOrNull()
    }

    // ========== DELETE ==========

    @Transactional
    suspend fun archiveEvent(eventId: UUID) {
        eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId)
        eventRepository.deleteById(eventId)
    }

    // ========== PRIVATE ==========

    private suspend fun getEventData(events: List<Events>): List<EventDto> = coroutineScope {
        val responderIds = events.mapNotNull { it.respondedBy }.distinct()

        val responders = if (responderIds.isNotEmpty()) {
            async {
                userRepository.findAllById(responderIds).toList().associateBy { it.id }
            }
        } else null

        val responderMap = responders?.await() ?: emptyMap()

        events.map { event ->
            event.toDto(responder = responderMap[event.respondedBy])
        }
    }
}


