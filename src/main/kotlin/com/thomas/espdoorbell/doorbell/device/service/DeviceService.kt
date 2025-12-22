package com.thomas.espdoorbell.doorbell.device.service

import com.thomas.espdoorbell.doorbell.core.exception.DeviceAccessNotFoundException
import com.thomas.espdoorbell.doorbell.core.exception.DeviceAlreadyExistsException
import com.thomas.espdoorbell.doorbell.core.exception.DeviceNotFoundException
import com.thomas.espdoorbell.doorbell.core.exception.UserAlreadyHasAccessException
import com.thomas.espdoorbell.doorbell.device.dto.DeviceDto
import com.thomas.espdoorbell.doorbell.device.entity.Devices
import com.thomas.espdoorbell.doorbell.device.repository.DeviceRepository
import com.thomas.espdoorbell.doorbell.device.request.DeviceAccessRequest
import com.thomas.espdoorbell.doorbell.device.request.DeviceRegister
import com.thomas.espdoorbell.doorbell.device.request.DeviceUpdateRequest
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
            ?: throw DeviceNotFoundException(deviceId)

    @Transactional(readOnly = true)
    suspend fun getDeviceByIdentifier(identifier: String): DeviceDto =
        deviceRepository.findByIdentifier(identifier).firstOrNull()?.toDto()
            ?: throw DeviceNotFoundException(identifier)

    suspend fun listDeviceAccess(deviceId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByDevice(deviceId).map { it.toDto() }

    // ========== CREATE ==========

    /**
     * Create a new device and auto-assign the creator as OWNER.
     * @param device Device registration details
     * @param creatorId UUID of the user creating the device (becomes OWNER)
     */
    @Transactional
    suspend fun createDevice(device: DeviceRegister, creatorId: UUID): DeviceDto {
        if (deviceRepository.existsByIdentifier(device.deviceID)) {
            throw DeviceAlreadyExistsException(device.deviceID)
        }
        
        val savedDevice = deviceRepository.save(device.toEntity())
        
        // Auto-assign creator as OWNER of the device
        val ownerAccess = UserDeviceAccess(
            user = creatorId,
            device = savedDevice.id!!,
            role = UserDeviceRole.OWNER
        )
        userDeviceAccessRepository.save(ownerAccess)
        
        return savedDevice.toDto()
    }

    // ========== UPDATE ==========

    @Transactional
    suspend fun updateDevice(deviceId: UUID, request: DeviceUpdateRequest): DeviceDto {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw DeviceNotFoundException(deviceId)

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
            ?: throw DeviceNotFoundException(deviceId)
        deviceRepository.deleteById(deviceId)
    }

    // ========== OWNERSHIP & ACCESS MANAGEMENT ==========

    /**
     * Verify that a user is the OWNER of a specific device.
     * @throws AccessDeniedException if user is not the owner
     */
    suspend fun verifyOwnership(deviceId: UUID, userId: UUID) {
        val access = userDeviceAccessRepository.findByDeviceAndUser(deviceId, userId)
            .firstOrNull() ?: throw AccessDeniedException("No access to device '$deviceId'")
        
        if (access.springAuthority != "ROLE_ADMIN") {
            throw AccessDeniedException("Only device owner can perform this action")
        }
    }

    /**
     * Check if a user has any access (OWNER or MEMBER) to a device.
     */
    suspend fun hasAccess(deviceId: UUID, userId: UUID): Boolean =
        userDeviceAccessRepository.findByDeviceAndUser(deviceId, userId)
            .firstOrNull() != null

    /**
     * Grant access to a device for a user.
     * @param deviceId Device to grant access to
     * @param request Contains userId and role to grant
     * @param grantedBy UUID of the user granting access (must be owner)
     */
    @Transactional
    suspend fun grantDeviceAccess(
        deviceId: UUID,
        request: DeviceAccessRequest,
        grantedBy: UUID
    ): UserDeviceAccessDto {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw DeviceNotFoundException(deviceId)

        // Verify the granter is the owner
        verifyOwnership(deviceId, grantedBy)

        // Check if user already has access
        val existingAccess = userDeviceAccessRepository
            .findByDeviceAndUser(deviceId, request.userId)
            .firstOrNull()
        
        if (existingAccess != null) {
            throw UserAlreadyHasAccessException(deviceId, request.userId)
        }

        val newAccess = UserDeviceAccess(
            user = request.userId,
            device = deviceId,
            role = request.role
        )
        
        return userDeviceAccessRepository.save(newAccess).toDto()
    }

    /**
     * Update a user's role for a device.
     */
    @Transactional
    suspend fun updateDeviceAccess(
        deviceId: UUID,
        targetUserId: UUID,
        newRole: UserDeviceRole,
        updatedBy: UUID
    ): UserDeviceAccessDto {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw DeviceNotFoundException(deviceId)

        // Verify the updater is the owner
        verifyOwnership(deviceId, updatedBy)

        // Find existing access
        val existingAccess = userDeviceAccessRepository
            .findByDeviceAndUser(deviceId, targetUserId)
            .firstOrNull() ?: throw DeviceAccessNotFoundException(deviceId, targetUserId)

        // Update via template (since entity is immutable)
        val query = Query.query(
            Criteria.where("device_id").`is`(deviceId)
                .and("user_id").`is`(targetUserId)
        )
        val update = Update.update("role", newRole.name)
        r2dbcEntityTemplate.update(query, update, UserDeviceAccess::class.java).awaitSingleOrNull()

        // Return updated DTO
        return existingAccess.toDto()
    }

    /**
     * Revoke a user's access to a device.
     */
    @Transactional
    suspend fun revokeDeviceAccess(
        deviceId: UUID,
        targetUserId: UUID,
        revokedBy: UUID
    ) {
        // Verify device exists
        deviceRepository.findById(deviceId)
            ?: throw DeviceNotFoundException(deviceId)

        // Verify the revoker is the owner
        verifyOwnership(deviceId, revokedBy)

        // Prevent owner from revoking their own access
        if (targetUserId == revokedBy) {
            throw AccessDeniedException("Cannot revoke your own owner access")
        }

        // Find and delete access
        val existingAccess = userDeviceAccessRepository
            .findByDeviceAndUser(deviceId, targetUserId)
            .firstOrNull() ?: throw DeviceAccessNotFoundException(deviceId, targetUserId)

        userDeviceAccessRepository.delete(existingAccess)
    }
}
