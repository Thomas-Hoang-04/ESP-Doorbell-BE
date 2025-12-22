package com.thomas.espdoorbell.doorbell.device.controller

import com.thomas.espdoorbell.doorbell.device.dto.DeviceDto
import com.thomas.espdoorbell.doorbell.device.request.DeviceAccessRequest
import com.thomas.espdoorbell.doorbell.device.request.DeviceRegister
import com.thomas.espdoorbell.doorbell.device.request.DeviceUpdateRequest
import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.shared.types.UserDeviceRole
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import jakarta.validation.Valid
import kotlinx.coroutines.flow.toList
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/devices")
class DeviceController(
    private val deviceService: DeviceService
) {

    // ========== READ OPERATIONS ==========

    /**
     * List all devices. Any authenticated user can access.
     */
    @GetMapping
    suspend fun listDevices(): List<DeviceDto> =
        deviceService.listDevices().toList()

    /**
     * List active devices only.
     */
    @GetMapping("/active")
    suspend fun listActiveDevices(): List<DeviceDto> =
        deviceService.listActiveDevices().toList()

    /**
     * List devices that have been online within the specified time window.
     */
    @GetMapping("/online")
    suspend fun listOnlineDevices(
        @RequestParam(defaultValue = "5") withinMinutes: Int
    ): List<DeviceDto> =
        deviceService.listOnlineDevices(withinMinutes).toList()

    /**
     * List devices with low battery.
     */
    @GetMapping("/low-battery")
    suspend fun listLowBatteryDevices(
        @RequestParam(defaultValue = "20") threshold: Int
    ): List<DeviceDto> =
        deviceService.listLowBatteryDevices(threshold).toList()

    /**
     * Get a specific device by ID.
     */
    @GetMapping("/{id}")
    suspend fun getDevice(@PathVariable id: UUID): DeviceDto =
        deviceService.getDevice(id)

    /**
     * Get a device by its hardware identifier.
     */
    @GetMapping("/identifier/{identifier}")
    suspend fun getDeviceByIdentifier(@PathVariable identifier: String): DeviceDto =
        deviceService.getDeviceByIdentifier(identifier)

    /**
     * List all users who have access to a device.
     * Only device owner can view access list.
     */
    @GetMapping("/{id}/access")
    suspend fun listDeviceAccess(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): List<UserDeviceAccessDto> {
        deviceService.verifyOwnership(id, principal.id)
        return deviceService.listDeviceAccess(id).toList()
    }

    // ========== CREATE OPERATIONS ==========

    /**
     * Register a new device.
     * Any authenticated user can create a device and becomes its OWNER.
     */
    @PostMapping
    suspend fun createDevice(
        @Valid @RequestBody request: DeviceRegister,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DeviceDto> {
        val device = deviceService.createDevice(request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(device)
    }

    // ========== UPDATE OPERATIONS ==========

    /**
     * Update device details.
     * Only device owner can update.
     */
    @PatchMapping("/{id}")
    suspend fun updateDevice(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DeviceUpdateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): DeviceDto {
        deviceService.verifyOwnership(id, principal.id)
        return deviceService.updateDevice(id, request)
    }

    // ========== DELETE OPERATIONS ==========

    /**
     * Delete a device.
     * Only device owner can delete.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteDevice(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ) {
        deviceService.verifyOwnership(id, principal.id)
        deviceService.deleteDevice(id)
    }

    // ========== ACCESS MANAGEMENT ==========

    /**
     * Grant a user access to a device.
     * Only device owner can grant access.
     */
    @PostMapping("/{id}/access")
    suspend fun grantDeviceAccess(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DeviceAccessRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<UserDeviceAccessDto> {
        val access = deviceService.grantDeviceAccess(id, request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(access)
    }

    /**
     * Update a user's role for a device.
     * Only device owner can update roles.
     */
    @PatchMapping("/{id}/access/{userId}")
    suspend fun updateDeviceAccess(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
        @RequestParam role: UserDeviceRole,
        @AuthenticationPrincipal principal: UserPrincipal
    ): UserDeviceAccessDto =
        deviceService.updateDeviceAccess(id, userId, role, principal.id)

    /**
     * Revoke a user's access to a device.
     * Only device owner can revoke access.
     */
    @DeleteMapping("/{id}/access/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun revokeDeviceAccess(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ) {
        deviceService.revokeDeviceAccess(id, userId, principal.id)
    }
}
