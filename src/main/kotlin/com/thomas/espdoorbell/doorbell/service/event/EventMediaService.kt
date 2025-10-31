package com.thomas.espdoorbell.doorbell.service.event

import com.thomas.espdoorbell.doorbell.model.dto.event.EventMediaDto
import com.thomas.espdoorbell.doorbell.model.entity.events.EventMedia
import com.thomas.espdoorbell.doorbell.repository.event.EventMediaRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EventMediaService(
    private val eventMediaRepository: EventMediaRepository,
) {

    @Transactional(readOnly = true)
    fun listMedia(): List<EventMediaDto> = eventMediaRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    fun getMedia(eventId: UUID): EventMediaDto =
        eventMediaRepository.findByIdOrNull(eventId)?.toDto()
            ?: throw EntityNotFoundException("Media for event $eventId was not found")

    @Transactional
    // TODO: Update HTTP request format here
    fun upsertMedia(media: EventMedia): EventMediaDto =
        eventMediaRepository.save(media).toDto()
}
