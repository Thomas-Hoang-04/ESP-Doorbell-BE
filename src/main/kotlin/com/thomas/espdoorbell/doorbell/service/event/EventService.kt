package com.thomas.espdoorbell.doorbell.service.event

import com.thomas.espdoorbell.doorbell.model.dto.event.EventDto
import com.thomas.espdoorbell.doorbell.model.dto.event.EventMediaDto
import com.thomas.espdoorbell.doorbell.model.dto.event.EventStreamDto
import com.thomas.espdoorbell.doorbell.model.dto.event.NotificationDto
import com.thomas.espdoorbell.doorbell.model.entity.events.Events
import com.thomas.espdoorbell.doorbell.model.types.StreamStatus
import com.thomas.espdoorbell.doorbell.repository.event.EventMediaRepository
import com.thomas.espdoorbell.doorbell.repository.event.EventRepository
import com.thomas.espdoorbell.doorbell.repository.event.EventStreamRepository
import com.thomas.espdoorbell.doorbell.repository.event.NotificationRepository
import com.thomas.espdoorbell.doorbell.repository.user.UserProfileRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.collections.get
import kotlin.collections.map

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventMediaRepository: EventMediaRepository,
    private val eventStreamRepository: EventStreamRepository,
    private val notificationRepository: NotificationRepository,
    private val userProfileRepository: UserProfileRepository,
) {

    private suspend fun getEventData(
        events: List<Events>,
        optInNotifications: Boolean,
    ): List<EventDto> = coroutineScope {
        val ids = events.map { it.id }

        val streamEvents = async {
            eventStreamRepository
                .findAllById(ids)
                .toList()
                .associateBy { it.id }
        }

        val mediaEvents = async {
            eventMediaRepository
                .findAllById(ids)
                .toList()
                .associateBy { it.id }
        }

        val responders = async {
            val resIds = events.mapNotNull { it.respondedBy }.distinct()
            userProfileRepository
                .findAllById(resIds)
                .toList()
                .associateBy { it.id }
        }

        val notifications = if (optInNotifications) async {
            notificationRepository
                .findAllByEventIds(ids)
                .toList()
                .groupBy { it.event }
        } else null

        val streamList = streamEvents.await()
        val mediaList = mediaEvents.await()
        val responderList = responders.await()
        val notificationList = notifications?.await() ?: emptyMap()

        events.map { event ->
            val id = event.id
            val stream = streamList[id]
            val media = mediaList[id]
            val responder = responderList[event.respondedBy]
            val notifications = notificationList[id] ?: emptyList()

            event.toDto(responder, stream, media, notifications)
        }
    }

    @Transactional(readOnly = true)
    suspend fun listEvents(optInNotifications: Boolean): List<EventDto> {
        val events = eventRepository.findAll().toList()

        if (events.isEmpty()) return emptyList()

        return getEventData(events, optInNotifications)
    }

    @Transactional(readOnly = true)
    suspend fun listEventsByDevice(deviceId: UUID, optInNotifications: Boolean): List<EventDto> {
        val events = eventRepository.findAllByDeviceId(deviceId).toList()

        if (events.isEmpty()) return emptyList()

        return getEventData(events, optInNotifications)
    }

    @Transactional(readOnly = true)
    suspend fun getEvent(eventId: UUID, optInNotifications: Boolean): EventDto {
        val event = eventRepository.findById(eventId)
            ?: throw IllegalArgumentException("Event with id $eventId was not found")

        return getEventData(listOf(event), optInNotifications).first()
    }

//    @Transactional
    // TODO: Implement event recording
//    fun recordEvent(event: EventRegister): EventDto
//        = eventRepository.save(event).toDto(false)

    fun listMedia(): Flow<EventMediaDto> = eventMediaRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun getMedia(eventId: UUID): EventMediaDto =
        eventMediaRepository.findById(eventId)?.toDto()
            ?: throw IllegalArgumentException("Media for event $eventId was not found")

    fun listStreams(): Flow<EventStreamDto> = eventStreamRepository.findAll().map { it.toDto() }

    fun listStreamsByStatus(status: StreamStatus): Flow<EventStreamDto> =
        eventStreamRepository.findAllByStreamStatus(status).map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun getStream(eventId: UUID): EventStreamDto =
        eventStreamRepository.findById(eventId)?.toDto()
            ?: throw IllegalArgumentException("Stream for event $eventId was not found")

    fun listNotifications(): Flow<NotificationDto> =
        notificationRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun getNotification(notificationId: UUID): NotificationDto =
        notificationRepository.findById(notificationId)?.toDto()
            ?: throw IllegalArgumentException("Notification with id $notificationId was not found")

    fun listNotificationsForEvent(eventId: UUID): Flow<NotificationDto> =
        notificationRepository.findAllByEventId(eventId).map { it.toDto() }
}
