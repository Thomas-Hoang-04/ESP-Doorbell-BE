package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.entity.Devices
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import com.thomas.espdoorbell.doorbell.model.types.EventType
import com.thomas.espdoorbell.doorbell.model.types.ResponseType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.OffsetDateTime

@Entity
@Table(name = "events")
class Events(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", referencedColumnName = "id", nullable = false)
    private val dev: Devices,

    @Column(name = "event_timestamp", nullable = false)
    private val eventTimestamp: OffsetDateTime = OffsetDateTime.now(),

    @Enumerated(value = EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "event_type", nullable = false)
    private val eventType: EventType = EventType.DOORBELL_RING,

    @Enumerated(value = EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "event_type", nullable = false)
    private val resType: ResponseType = ResponseType.PENDING,

    @Column(name = "event_timestamp", nullable = false)
    private val resTimestamp: OffsetDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by", referencedColumnName = "id")
    private val responder: UserCredentials? = null,
): BaseEntity() {
    @PrePersist
    @PreUpdate
    fun validateTime() {
        resTimestamp?.let {
            require(it.isAfter(eventTimestamp)) {
                "Response timestamp must be after event timestamp"
            }
        }
    }
}