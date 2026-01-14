package com.thomas.espdoorbell.doorbell.event.service

import com.thomas.espdoorbell.doorbell.core.exception.DomainException
import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.event.entity.Events
import com.thomas.espdoorbell.doorbell.event.repository.EventRepository
import com.thomas.espdoorbell.doorbell.event.request.EventCreateRequest
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class EventService(
    private val eventRepository: EventRepository
) {
    @Transactional(readOnly = true)
    suspend fun listEvents(): List<EventDto> =
        eventRepository.findAll().toList().map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun listEventsByDevice(deviceId: UUID): List<EventDto> =
        eventRepository.findAllByDeviceId(deviceId).toList().map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun listEventsByDateRange(start: OffsetDateTime, end: OffsetDateTime): List<EventDto> {
        require(!start.isAfter(end)) { "Start date must not be after end date" }
        return eventRepository.findByCreatedAtBetween(start, end).toList().map { it.toDto() }
    }

    @Transactional(readOnly = true)
    suspend fun listRecentEvents(limit: Int = 10): List<EventDto> {
        require(limit > 0) { "Limit must be positive" }
        return eventRepository.findRecent(limit).toList().map { it.toDto() }
    }

    @Transactional(readOnly = true)
    suspend fun getEvent(eventId: UUID): EventDto {
        val event = eventRepository.findById(eventId)
            ?: throw DomainException.EntityNotFound.Event("id", eventId.toString())
        return event.toDto()
    }

    suspend fun getEventCountByDevice(deviceId: UUID): Long =
        eventRepository.countByDeviceId(deviceId)

    @Transactional
    suspend fun createEvent(request: EventCreateRequest): EventDto {
        val event = Events(
            deviceId = request.deviceId,
            eventType = request.eventType
        )
        val savedEvent = eventRepository.save(event)
        return savedEvent.toDto()
    }

    @Transactional
    suspend fun deleteEvent(eventId: UUID) {
        eventRepository.findById(eventId)
            ?: throw DomainException.EntityNotFound.Event("id", eventId.toString())
        eventRepository.deleteById(eventId)
    }
}
