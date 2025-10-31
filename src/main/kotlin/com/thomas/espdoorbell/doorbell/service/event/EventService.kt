package com.thomas.espdoorbell.doorbell.service.event

import com.thomas.espdoorbell.doorbell.model.dto.event.EventDto
import com.thomas.espdoorbell.doorbell.model.entity.events.Events
import com.thomas.espdoorbell.doorbell.repository.event.EventRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EventService(
    private val eventRepository: EventRepository,
) {

    @Transactional(readOnly = true)
    fun listEvents(optInNotifications: Boolean): List<EventDto>
        = eventRepository.findAll().map { it.toDto(optInNotifications) }

    @Transactional(readOnly = true)
    fun listEventsByDevice(deviceId: UUID, optInNotifications: Boolean): List<EventDto> =
        eventRepository.findAllByDeviceId(deviceId).map { it.toDto(optInNotifications) }

    @Transactional(readOnly = true)
    fun getEvent(eventId: UUID, optInNotifications: Boolean): EventDto =
        eventRepository.findByIdOrNull(eventId)?.toDto(optInNotifications)
            ?: throw EntityNotFoundException("Event with id $eventId was not found")

    @Transactional
    // TODO: Update HTTP request format here
    fun recordEvent(event: Events): EventDto = eventRepository.save(event).toDto(false)
}
