package com.thomas.espdoorbell.doorbell.model.entity.events

import com.thomas.espdoorbell.doorbell.model.entity.Devices
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.entity.user.UserCredentials
import com.thomas.espdoorbell.doorbell.model.types.EventType
import com.thomas.espdoorbell.doorbell.model.types.ResponseType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.OffsetDateTime

@Entity
@Table(name = "events")
class Events(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", referencedColumnName = "id", nullable = false)
    private val device: Devices,

    @Column(name = "event_timestamp", nullable = false)
    private val eventTimestamp: OffsetDateTime = OffsetDateTime.now(),

    @Enumerated(value = EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "event_type", nullable = false)
    private val eventType: EventType = EventType.DOORBELL_RING,

    @Enumerated(value = EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "response_type", nullable = false)
    private val responseType: ResponseType = ResponseType.PENDING,

    @Column(name = "response_timestamp")
    private val responseTimestamp: OffsetDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by", referencedColumnName = "id")
    private val respondedBy: UserCredentials? = null,
): BaseEntity() {
    @OneToOne(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, optional = true)
    private val stream: StreamEvents? = null

    @OneToOne(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, optional = true)
    private val media: EventMedia? = null

    @OneToMany(mappedBy = "event", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    private val notifications: MutableList<Notifications> = mutableListOf()

    @PrePersist
    @PreUpdate
    fun validateResponseWindow() {
        responseTimestamp?.let {
            require(!it.isBefore(eventTimestamp)) {
                "Response timestamp must be on or after the event timestamp"
            }
        }
    }
}