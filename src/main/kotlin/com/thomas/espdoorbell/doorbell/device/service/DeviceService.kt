package com.thomas.espdoorbell.doorbell.device.service

import com.thomas.espdoorbell.doorbell.device.dto.DeviceDto
import com.thomas.espdoorbell.doorbell.device.entity.Devices
import com.thomas.espdoorbell.doorbell.device.repository.DeviceRepository
import com.thomas.espdoorbell.doorbell.device.request.DeviceRegister
import com.thomas.espdoorbell.doorbell.device.request.DeviceUpdateRequest
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.user.repository.UserDeviceAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate
) {
    // ========== READ ==========
    
    fun listDevices(): Flow<DeviceDto> = deviceRepository.findAll().map { it.toDto() }

    fun listActiveDevices(): Flow<DeviceDto> = 
        deviceRepository.findAllByIsActiveTrue().map { it.toDto() }

    fun listOnlineDevices(withinMinutes: Int = 5): Flow<DeviceDto> =
        deviceRepository.findOnlineWithin(withinMinutes).map { it.toDto() }

    fun listLowBatteryDevices(threshold: Int = 20): Flow<DeviceDto> =
        deviceRepository.findByBatteryLevelLessThan(threshold).map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun getDevice(deviceId: UUID): DeviceDto =
        deviceRepository.findById(deviceId)?.toDto()
            ?: throw NoSuchElementException("Device with id $deviceId was not found")

    @Transactional(readOnly = true)
    suspend fun getDeviceByIdentifier(identifier: String): DeviceDto =
        deviceRepository.findByIdentifier(identifier).firstOrNull()?.toDto()
            ?: throw NoSuchElementException("Device with identifier $identifier was not found")

    suspend fun listDeviceAccess(deviceId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByDeviceId(deviceId).map { it.toDto() }

    // ========== CREATE ==========

    @Transactional
    suspend fun registerDevice(device: DeviceRegister): DeviceDto {
        if (deviceRepository.existsByIdentifier(device.deviceID)) {
            throw IllegalStateException("Device with identifier ${device.deviceID} already exists")
        }
        return deviceRepository.save(device.toEntity()).toDto()
    }

    // ========== UPDATE ==========

    @Transactional
    suspend fun updateDevice(deviceId: UUID, request: DeviceUpdateRequest): DeviceDto {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw NoSuchElementException("Device with id $deviceId was not found")

        val query = Query.query(Criteria.where("id").`is`(deviceId))
        
        // Build update dynamically from non-null request fields
        val updates = mutableMapOf<String, Any?>()
        request.displayName?.let { updates["name"] = it }
        request.locationDescription?.let { updates["location"] = it }
        request.modelName?.let { updates["model"] = it }
        request.firmwareVersion?.let { updates["firmware_version"] = it }
        request.isActive?.let { updates["is_active"] = it }

        if (updates.isNotEmpty()) {
            var update: Update? = null
            for ((key, value) in updates) {
                update = update?.set(key, value) ?: Update.update(key, value)
            }
            update?.let { r2dbcEntityTemplate.update(query, it, Devices::class.java) }
        }

        return getDevice(deviceId)
    }

    /**
     * Update device from heartbeat message (unified update)
     */
    @Transactional
    suspend fun updateDeviceFromHeartbeat(
        deviceId: UUID,
        isActive: Boolean,
        batteryLevel: Int?,
        signalStrength: Int?
    ) {
        val query = Query.query(Criteria.where("id").`is`(deviceId))
        
        val updates = mutableMapOf<String, Any>(
            "last_online" to OffsetDateTime.now(),
            "is_active" to isActive
        )
        
        batteryLevel?.let { updates["battery_level"] = it }
        signalStrength?.let { updates["signal_strength"] = it }
        
        var update: Update? = null
        for ((key, value) in updates) {
            update = update?.set(key, value) ?: Update.update(key, value)
        }
        
        update?.let { r2dbcEntityTemplate.update(query, it, Devices::class.java) }
    }

    // ========== DELETE ==========

    @Transactional
    suspend fun deleteDevice(deviceId: UUID) {
        deviceRepository.findById(deviceId)
            ?: throw NoSuchElementException("Device with id $deviceId was not found")
        deviceRepository.deleteById(deviceId)
    }
}

