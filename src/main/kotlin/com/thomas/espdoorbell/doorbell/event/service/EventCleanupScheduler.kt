package com.thomas.espdoorbell.doorbell.event.service

import com.thomas.espdoorbell.doorbell.event.repository.EventImageRepository
import com.thomas.espdoorbell.doorbell.event.repository.EventRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class EventCleanupScheduler(
    private val eventRepository: EventRepository,
    private val eventImageRepository: EventImageRepository,
    private val storageService: StorageService
) {
    private val logger = LoggerFactory.getLogger(EventCleanupScheduler::class.java)

    @Scheduled(cron = "0 0 3 * * *")
    fun cleanupOldEvents() = runBlocking {
        val cutoff = OffsetDateTime.now().minusDays(30)
        val events = eventRepository.findAllByEventTimestampBefore(cutoff).toList()

        if (events.isEmpty()) {
            return@runBlocking
        }

        var deletedEvents = 0
        events.forEach { event ->
            val eventId = event.id ?: return@forEach
            try {
                val images = eventImageRepository.findAllByEventId(eventId).toList()
                images.forEach { storageService.deleteEventImage(it.filePath) }

                if (images.isNotEmpty()) {
                    eventImageRepository.deleteAll(images)
                }

                eventRepository.deleteById(eventId)
                storageService.deleteEventDirectory(eventId)
                deletedEvents++
            } catch (e: Exception) {
                logger.warn("Failed to clean up event {}", eventId, e)
            }
        }

        logger.info("Cleaned up {} events older than {}", deletedEvents, cutoff)
    }
}
