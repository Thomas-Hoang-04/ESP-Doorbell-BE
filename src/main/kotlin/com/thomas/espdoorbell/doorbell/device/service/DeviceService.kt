package com.thomas.espdoorbell.doorbell.device.service

import com.thomas.espdoorbell.doorbell.core.exception.DomainException
import com.thomas.espdoorbell.doorbell.device.dto.DeviceDto
import com.thomas.espdoorbell.doorbell.device.entity.Devices
import com.thomas.espdoorbell.doorbell.device.repository.DeviceRepository
import com.thomas.espdoorbell.doorbell.device.request.DeviceAccessRequest
import com.thomas.espdoorbell.doorbell.device.request.DeviceRegister
import com.thomas.espdoorbell.doorbell.device.request.DeviceUpdateRequest
import com.thomas.espdoorbell.doorbell.mqtt.service.MqttPublisherService
import com.thomas.espdoorbell.doorbell.shared.types.UserDeviceRole
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.user.entity.UserDeviceAccess
import com.thomas.espdoorbell.doorbell.user.repository.UserDeviceAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.*

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate,
    private val passwordEncoder: Argon2PasswordEncoder,
    private val mqttPublisherService: MqttPublisherService
) {

    
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
            ?: throw DomainException.EntityNotFound.Device("id", deviceId.toString())

    @Transactional(readOnly = true)
    suspend fun getDeviceByIdentifier(identifier: String): DeviceDto =
        deviceRepository.findByDeviceId(identifier)?.toDto()
            ?: throw DomainException.EntityNotFound.Device("identifier", identifier)

    suspend fun listDeviceAccess(deviceId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByDevice(deviceId).map { it.toDto() }



    @Transactional
    suspend fun createDevice(device: DeviceRegister, creatorId: UUID): DeviceDto {
        if (deviceRepository.existsByIdentifier(device.deviceID)) {
            throw DomainException.EntityConflict.DeviceAlreadyExists(device.deviceID)
        }

        val hashedKey = passwordEncoder.encode(device.deviceKey)
        val savedDevice = deviceRepository.save(device.toEntity(hashedKey))
        
        // Auto-assign creator as OWNER of the device
        val ownerAccess = UserDeviceAccess(
            user = creatorId,
            device = savedDevice.id!!,
            role = UserDeviceRole.OWNER
        )
        userDeviceAccessRepository.save(ownerAccess)
        
        return savedDevice.toDto()
    }



    @Transactional
    suspend fun updateDevice(deviceId: UUID, request: DeviceUpdateRequest): Boolean {
        val device = deviceRepository.findById(deviceId)
            ?: throw DomainException.EntityNotFound.Device("id", deviceId.toString())

        val query = Query.query(Criteria.where("id").`is`(deviceId))
        
        val updates = mutableMapOf<String, Any?>()
        request.displayName?.let { updates["name"] = it }
        request.locationDescription?.let { updates["location"] = it }
        request.modelName?.let { updates["model"] = it }
        request.firmwareVersion?.let { updates["firmware_version"] = it }
        request.isActive?.let { updates["is_active"] = it }
        request.chimeIndex?.let { 
            require(it in 1..4) { "Chime index must be between 1 and 4" }
            updates["chime_index"] = it 
        }
        request.volumeLevel?.let {
            require(it in 0..100) { "Volume level must be between 0 and 100" }
            updates["volume_level"] = it
        }

        if (updates.isEmpty()) { return false }

        var update: Update? = null
        for ((key, value) in updates) {
            update = update?.set(key, value) ?: Update.update(key, value)
        }
        
        val result = update?.let { r2dbcEntityTemplate.update(query, it, Devices::class.java).awaitSingleOrNull() }
        val updated = (result ?: 0L) > 0

        if (updated && request.chimeIndex != null) {
            mqttPublisherService.publishSetChime(device.deviceId, request.chimeIndex)
        }
        if (updated && request.volumeLevel != null) {
            mqttPublisherService.publishSetVolume(device.deviceId, request.volumeLevel)
        }

        return updated
    }


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
        
        update?.let { r2dbcEntityTemplate.update(query, it, Devices::class.java).awaitSingleOrNull() }
    }



    @Transactional
    suspend fun deleteDevice(deviceId: UUID) {
        deviceRepository.findById(deviceId)
            ?: throw DomainException.EntityNotFound.Device("id", deviceId.toString())
        deviceRepository.deleteById(deviceId)
    }




    suspend fun verifyOwnership(deviceId: UUID, userId: UUID) {
        val access = userDeviceAccessRepository.findByDeviceAndUser(deviceId, userId)
            .firstOrNull() ?: throw AccessDeniedException("No access to device '$deviceId'")
        
        if (access.springAuthority != "ROLE_ADMIN") {
            throw AccessDeniedException("Only device owner can perform this action")
        }
    }


    suspend fun hasAccess(deviceId: UUID, userId: UUID): Boolean =
        userDeviceAccessRepository.findByDeviceAndUser(deviceId, userId)
            .firstOrNull() != null

    suspend fun verifyDeviceKey(deviceId: String, rawDeviceKey: String): Boolean {
        val device = deviceRepository.findByDeviceId(deviceId) ?: return false
        return passwordEncoder.matches(rawDeviceKey, device.deviceKey)
    }


    @Transactional
    suspend fun grantDeviceAccess(
        deviceId: UUID,
        request: DeviceAccessRequest,
        grantedBy: UUID
    ): UserDeviceAccessDto {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw DomainException.EntityNotFound.Device("id", deviceId.toString())

        verifyOwnership(deviceId, grantedBy)

        // Check if user already has access
        val existingAccess = userDeviceAccessRepository
            .findByDeviceAndUser(deviceId, request.userId)
            .firstOrNull()
        
        if (existingAccess != null) {
            throw DomainException.EntityConflict.UserAlreadyHasAccess(deviceId, request.userId)
        }

        val newAccess = UserDeviceAccess(
            user = request.userId,
            device = deviceId,
            role = request.role
        )
        
        return userDeviceAccessRepository.save(newAccess).toDto()
    }


    @Transactional
    suspend fun updateDeviceAccess(
        deviceId: UUID,
        targetUserId: UUID,
        newRole: UserDeviceRole,
        updatedBy: UUID
    ): Boolean {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw DomainException.EntityNotFound.Device("id", deviceId.toString())

        verifyOwnership(deviceId, updatedBy)

        if (userDeviceAccessRepository.findByDeviceAndUser(deviceId, targetUserId).firstOrNull() == null) {
            throw DomainException.AccessDenied(targetUserId.toString(), deviceId.toString())
        }

        // Update via template (since entity is immutable)
        val query = Query.query(
            Criteria.where("device_id").`is`(deviceId)
                .and("user_id").`is`(targetUserId)
        )
        val update = Update.update("role", newRole.name)
        val result = r2dbcEntityTemplate.update(query, update, UserDeviceAccess::class.java).awaitSingleOrNull()
        return (result ?: 0L) > 0
    }


    @Transactional
    suspend fun revokeDeviceAccess(
        deviceId: UUID,
        targetUserId: UUID,
        revokedBy: UUID
    ) {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw DomainException.EntityNotFound.Device("id", deviceId.toString())

        verifyOwnership(deviceId, revokedBy)

        // Prevent owner from revoking their own access
        if (targetUserId == revokedBy) {
            throw AccessDeniedException("Cannot revoke your own owner access")
        }

        // Find and delete access
        val existingAccess = userDeviceAccessRepository
            .findByDeviceAndUser(deviceId, targetUserId)
            .firstOrNull() ?: throw DomainException.AccessDenied(targetUserId.toString(), deviceId.toString())

        userDeviceAccessRepository.delete(existingAccess)
    }
}
