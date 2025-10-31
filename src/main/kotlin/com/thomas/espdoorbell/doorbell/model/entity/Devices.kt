package com.thomas.espdoorbell.doorbell.model.entity

import com.thomas.espdoorbell.doorbell.model.dto.device.DeviceDto
import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.entity.events.Events
import com.thomas.espdoorbell.doorbell.model.entity.user.UserDeviceAccess
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
    @field:Min(value = 0)
    @field:Max(value = 100)
    private val batteryLevel: Int = 100,

    @Column(name = "signal_strength")
    @field:Min(value = -100)
    @field:Max(value = 0)
    private val signalStrength: Int? = null,

    @Column(name = "last_online")
    private val lastOnline: OffsetDateTime? = null,
): BaseEntity() {
    @OneToMany(mappedBy = "device", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    private val eventLogs: MutableList<Events> = mutableListOf()

    @OneToMany(mappedBy = "device", cascade = [CascadeType.ALL], fetch = FetchType.LAZY, orphanRemoval = true)
    private val accessAssignments: MutableSet<UserDeviceAccess> = mutableSetOf()

    fun toDto(): DeviceDto = DeviceDto(
        id = id,
        deviceIdentifier = deviceId,
        displayName = name,
        locationDescription = location,
        modelName = model,
        firmwareVersion = fwVersion,
        active = isActive,
        batteryLevelPercent = batteryLevel,
        signalStrengthDbm = signalStrength,
        lastOnlineAt = lastOnline
    )
}