package com.thomas.espdoorbell.doorbell.device.entity

import com.thomas.espdoorbell.doorbell.device.dto.DeviceDto
import com.thomas.espdoorbell.doorbell.shared.validation.Validatable
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table(name = "devices")
@Suppress("unused")
class Devices(
    @Id
    @Column("id")
    val id: UUID? = null,

    @Column("device_id")
    val deviceId: String,

    @Column("device_key")
    val deviceKey: String,

    @Column("name")
    val name: String,

    @Column("location")
    val location: String? = null,

    @Column("model")
    val model: String? = null,

    @Column("firmware_version")
    val fwVersion: String? = null,

    @Column("is_active")
    val isActive: Boolean = true,

    @Column("battery_level")
    val batteryLevel: Int = 100,

    @Column("signal_strength")
    val signalStrength: Int? = null,

    @Column("chime_index")
    val chimeIndex: Int = 1,

    @Column("volume_level")
    val volumeLevel: Int = 10,

    @Column("last_online")
    val lastOnline: OffsetDateTime? = null,

    @LastModifiedDate
    @Column("updated_at")
    val updatedAt: OffsetDateTime? = null,

    @CreatedDate
    @Column("created_at")
    val createdAt: OffsetDateTime? = null,
): Validatable {

    init { validate() }

    val displayName: String
        get() = name

    override fun validate() {
        require(deviceId.isNotBlank()) { "Device ID must not be blank" }
        require(deviceId.length <= 100) { "Device ID exceeds maximum length" }
        model?.let { require(it.length <= 100) { "Model name exceeds maximum length" } }
        fwVersion?.let { require(it.length <= 50) { "Firmware version exceeds maximum length" } }
        require(name.isNotBlank()) { "Device name must not be blank" }
        require(batteryLevel in 0..100) { "Battery level must be between 0 and 100" }
        require(signalStrength == null || signalStrength in -100..0) { "Signal strength must be between -100 and 0" }
        require(chimeIndex in 1..4) { "Chime index must be between 1 and 4" }
        require(volumeLevel in 0..100) { "Volume level must be between 0 and 100" }
    }

    fun toDto(): DeviceDto = DeviceDto(
        id = id!!,
        deviceIdentifier = deviceId,
        displayName = name,
        locationDescription = location,
        modelName = model,
        firmwareVersion = fwVersion,
        active = isActive,
        batteryLevelPercent = batteryLevel,
        signalStrengthDbm = signalStrength,
        chimeIndex = chimeIndex,
        volumeLevel = volumeLevel,
        lastOnlineAt = lastOnline
    )
}