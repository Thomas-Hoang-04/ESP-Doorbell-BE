package com.thomas.espdoorbell.doorbell.service.event

import com.thomas.espdoorbell.doorbell.model.dto.event.NotificationDto
import com.thomas.espdoorbell.doorbell.repository.event.NotificationRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {

    @Transactional(readOnly = true)
    fun listNotifications(): List<NotificationDto> =
        notificationRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    fun getNotification(notificationId: UUID): NotificationDto =
        notificationRepository.findByIdOrNull(notificationId)?.toDto()
            ?: throw EntityNotFoundException("Notification with id $notificationId was not found")

    @Transactional(readOnly = true)
    fun listNotificationsForEvent(eventId: UUID): List<NotificationDto> =
        notificationRepository.findAllByEventId(eventId).map { it.toDto() }
}
