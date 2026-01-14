package com.thomas.espdoorbell.doorbell.device.controller

import com.thomas.espdoorbell.doorbell.device.dto.DeviceDto
import com.thomas.espdoorbell.doorbell.device.request.DeviceAccessRequest
import com.thomas.espdoorbell.doorbell.device.request.DeviceRegister
import com.thomas.espdoorbell.doorbell.device.request.DeviceUpdateRequest
import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.shared.types.UserDeviceRole
import com.thomas.espdoorbell.doorbell.user.dto.AvailabilityResponse
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



    @GetMapping
    suspend fun listDevices(): List<DeviceDto> =
        deviceService.listDevices().toList()

    @GetMapping("/active")
    suspend fun listActiveDevices(): List<DeviceDto> =
        deviceService.listActiveDevices().toList()

    @GetMapping("/online")
    suspend fun listOnlineDevices(
        @RequestParam(defaultValue = "5") withinMinutes: Int
    ): List<DeviceDto> =
        deviceService.listOnlineDevices(withinMinutes).toList()

    @GetMapping("/low-battery")
    suspend fun listLowBatteryDevices(
        @RequestParam(defaultValue = "20") threshold: Int
    ): List<DeviceDto> =
        deviceService.listLowBatteryDevices(threshold).toList()

    @GetMapping("/{id}")
    suspend fun getDevice(@PathVariable id: UUID): DeviceDto =
        deviceService.getDevice(id)

    @GetMapping("/identifier/{identifier}")
    suspend fun getDeviceByIdentifier(@PathVariable identifier: String): DeviceDto =
        deviceService.getDeviceByIdentifier(identifier)

    @GetMapping("/{id}/access")
    suspend fun listDeviceAccess(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): List<UserDeviceAccessDto> {
        deviceService.verifyOwnership(id, principal.id)
        return deviceService.listDeviceAccess(id).toList()
    }



    @PostMapping
    suspend fun createDevice(
        @Valid @RequestBody request: DeviceRegister,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<DeviceDto> {
        val device = deviceService.createDevice(request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(device)
    }



    @PatchMapping("/{id}")
    suspend fun updateDevice(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DeviceUpdateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): AvailabilityResponse {
        deviceService.verifyOwnership(id, principal.id)
        return AvailabilityResponse(available = deviceService.updateDevice(id, request))
    }



    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteDevice(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ) {
        deviceService.verifyOwnership(id, principal.id)
        deviceService.deleteDevice(id)
    }



    @PostMapping("/{id}/access")
    suspend fun grantDeviceAccess(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DeviceAccessRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<UserDeviceAccessDto> {
        val access = deviceService.grantDeviceAccess(id, request, principal.id)
        return ResponseEntity.status(HttpStatus.CREATED).body(access)
    }

    @PatchMapping("/{id}/access/{userId}")
    suspend fun updateDeviceAccess(
        @PathVariable id: UUID,
        @PathVariable userId: UUID,
        @RequestParam role: UserDeviceRole,
        @AuthenticationPrincipal principal: UserPrincipal
    ): AvailabilityResponse =
        AvailabilityResponse(available = deviceService.updateDeviceAccess(id, userId, role, principal.id))

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
