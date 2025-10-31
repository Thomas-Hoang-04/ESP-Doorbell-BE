package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.dto.event.NotificationDto
import com.thomas.espdoorbell.doorbell.model.types.NotificationMethod
import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "notifications")
class Notifications(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", referencedColumnName = "id", nullable = false)
    private val event: Events,

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "notification_type", nullable = false)
    private val notificationType: NotificationMethod = NotificationMethod.PUSH,

    @Column(name = "recipient", nullable = false, length = 255)
    private val recipient: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", referencedColumnName = "id")
    private val recipientUser: UserCredentials? = null,

    @Column(name = "sent_at", insertable = false, updatable = false)
    private val sentAt: OffsetDateTime? = null,

    @Column(name = "success", nullable = false)
    private val success: Boolean = false,

    @Column(name = "error_message", columnDefinition = "TEXT")
    private val errorMessage: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private lateinit var id: UUID

    @PrePersist
    @PreUpdate
    fun validateRecipient() {
        require(recipient.isNotBlank()) { "Notification recipient must not be blank" }
    }

    fun toDto(): NotificationDto = NotificationDto(
        id = id,
        eventId = event.id,
        typeCode = notificationType.name,
        typeLabel = notificationType.toDisplayName(),
        recipientTarget = recipient,
        recipientUserId = recipientUser?.id,
        sentAt = sentAt,
        successful = success,
        errorMessage = errorMessage,
    )
}
