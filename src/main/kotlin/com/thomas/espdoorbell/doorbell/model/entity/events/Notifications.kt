package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.dto.event.NotificationDto
import com.thomas.espdoorbell.doorbell.model.types.NotificationMethod
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "notifications")
data class Notifications(
    @Column("id")
    @Id
    private val id: UUID = UUID.randomUUID(),

    @Column("event_id")
    private val _event: UUID,

    @Column("notification_type")
    private val notificationType: NotificationMethod = NotificationMethod.PUSH,

    @Column("recipient")
    private val recipient: String,

    @Column("recipient_user_id")
    private val recipientUser: UUID? = null,

    @Column("sent_at")
    @CreatedDate
    private val sentAt: OffsetDateTime? = null,

    @Column("success")
    private val success: Boolean = false,

    @Column("error_message")
    private val errorMessage: String? = null,
) {
    init { validateRecipient() }

    val event: UUID
        get() = _event

    fun validateRecipient() {
        require(recipient.isNotBlank()) { "Notification recipient must not be blank" }
    }

    fun updateStatus(status: Boolean): Notifications =
        copy(success = status, errorMessage = if (status) null else "Failed to send notification")

    fun toDto(): NotificationDto = NotificationDto(
        id = id,
        eventId = _event,
        typeCode = notificationType.name,
        typeLabel = notificationType.toDisplayName(),
        recipientTarget = recipient,
        recipientUserId = recipientUser,
        sentAt = sentAt,
        successful = success,
        errorMessage = errorMessage,
    )
}
