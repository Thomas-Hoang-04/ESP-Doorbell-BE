package com.thomas.espdoorbell.doorbell.model.entity

import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.entity.events.Events
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.OffsetDateTime

@Entity
@Table(name = "devices")
class Devices(
    @Column(name = "device_id", unique = true, nullable = false, length = 100)
    private val deviceId: String,

    @Column(name = "name", nullable = false)
    private val name: String,

    @Column(name = "location")
    private val location: String? = null,

    @Column(name = "model", length = 100)
    private val model: String? = null,

    @Column(name = "firmware_version", length = 50)
    private val fwVersion: String? = null,

    @Column(name = "is_active")
    private val isActive: Boolean = true,

    @Column(name = "battery_level", nullable = false)
    @param:Min(value = 0)
    @param:Max(value = 100)
    private val batteryLevel: Int = 100,

    @Column(name = "signal_strength")
    @param:Min(value = -100)
    @param:Max(value = 0)
    private val signalStrength: Int? = null,

    @Column(name = "last_online")
    private val lastOnline: OffsetDateTime? = null,
): BaseEntity() {
    @OneToMany(mappedBy = "device_id", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val eventLogs: MutableList<Events> = mutableListOf()
}