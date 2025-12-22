package com.thomas.espdoorbell.doorbell.user.controller

import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.user.dto.UserDto
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.user.request.PasswordUpdateRequest
import com.thomas.espdoorbell.doorbell.user.service.UserService
import jakarta.validation.Valid
import kotlinx.coroutines.flow.toList
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    suspend fun listUsers(
        @RequestParam(defaultValue = "false") includeAccessAssignments: Boolean
    ): List<UserDto> =
        userService.listUsers(includeAccessAssignments)

    @GetMapping("/me")
    suspend fun getCurrentUser(
        @AuthenticationPrincipal principal: UserPrincipal
    ): UserDto =
        userService.getUser(principal.id)

    @GetMapping("/{id}")
    suspend fun getUser(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): UserDto {
        if (principal.id != id && !hasAdminRole(principal)) {
            throw AccessDeniedException("Cannot access other users' profiles")
        }
        return userService.getUser(id)
    }

    @GetMapping("/{id}/devices")
    suspend fun listUserDevices(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): List<UserDeviceAccessDto> {
        if (principal.id != id && !hasAdminRole(principal)) {
            throw AccessDeniedException("Cannot access other users' devices")
        }
        return userService.listDeviceAccessForUser(id).toList()
    }

    @PatchMapping("/{id}/notification")
    suspend fun updateNotification(
        @PathVariable id: UUID,
        @RequestParam enabled: Boolean,
        @AuthenticationPrincipal principal: UserPrincipal
    ): UserDto {
        if (principal.id != id) {
            throw AccessDeniedException("Cannot update other users' settings")
        }
        return userService.updateNotificationEnabled(id, enabled)
    }

    @PatchMapping("/{id}/email")
    suspend fun updateEmail(
        @PathVariable id: UUID,
        @RequestParam email: String,
        @AuthenticationPrincipal principal: UserPrincipal
    ): UserDto {
        if (principal.id != id) {
            throw AccessDeniedException("Cannot update other users' email")
        }
        return userService.updateEmail(id, email)
    }

    @PatchMapping("/{id}/password")
    suspend fun updatePassword(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PasswordUpdateRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ) {
        if (principal.id != id) {
            throw AccessDeniedException("Cannot update other users' passwords")
        }
        userService.updatePassword(id, request.oldPassword, request.newPassword)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun deleteUser(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ) {
        if (principal.id == id) {
            throw AccessDeniedException("Cannot delete your own account via admin endpoint")
        }
        userService.deleteUser(id)
    }

    private fun hasAdminRole(principal: UserPrincipal): Boolean =
        principal.authorities.any { it.authority == "ROLE_ADMIN" }
}
