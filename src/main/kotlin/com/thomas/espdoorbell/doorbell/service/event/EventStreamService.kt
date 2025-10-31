package com.thomas.espdoorbell.doorbell.service.event

import com.thomas.espdoorbell.doorbell.model.dto.event.EventStreamDto
import com.thomas.espdoorbell.doorbell.model.entity.events.StreamEvents
import com.thomas.espdoorbell.doorbell.model.types.StreamStatus
import com.thomas.espdoorbell.doorbell.repository.event.EventStreamRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EventStreamService(
    private val eventStreamRepository: EventStreamRepository,
) {

    @Transactional(readOnly = true)
    fun listStreams(): List<EventStreamDto> = eventStreamRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    fun listStreamsByStatus(status: StreamStatus): List<EventStreamDto> =
        eventStreamRepository.findAllByStreamStatus(status).map { it.toDto() }

    @Transactional(readOnly = true)
    fun getStream(eventId: UUID): EventStreamDto =
        eventStreamRepository.findByIdOrNull(eventId)?.toDto()
            ?: throw EntityNotFoundException("Stream for event $eventId was not found")

    @Transactional
    // TODO: Update HTTP request format here
    fun upsertStream(streamEvents: StreamEvents): EventStreamDto =
        eventStreamRepository.save(streamEvents).toDto()
}
