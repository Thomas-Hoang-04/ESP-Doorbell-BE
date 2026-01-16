package com.thomas.espdoorbell.doorbell.event.controller

import com.thomas.espdoorbell.doorbell.core.firebase.NotificationService
import com.thomas.espdoorbell.doorbell.device.repository.DeviceRepository
import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.event.request.EventCreateRequest
import com.thomas.espdoorbell.doorbell.event.service.EventService
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import com.thomas.espdoorbell.doorbell.shared.types.EventType
import com.thomas.espdoorbell.doorbell.user.repository.UserDeviceAccessRepository
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.*

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventService: EventService,
    private val deviceService: DeviceService,
    private val deviceRepository: DeviceRepository,
    private val passwordEncoder: Argon2PasswordEncoder,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(EventController::class.java)
    @GetMapping
    suspend fun listEvents(): List<EventDto> =
        eventService.listEvents()

    @GetMapping("/device/{deviceId}")
    suspend fun listEventsByDevice(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): List<EventDto> {
        if (!deviceService.hasAccess(deviceId, principal.id)) {
            throw AccessDeniedException("No access to device")
        }
        return eventService.listEventsByDevice(deviceId)
    }

    @GetMapping("/range")
    suspend fun listEventsByDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: OffsetDateTime
    ): List<EventDto> =
        eventService.listEventsByDateRange(start, end)

    @GetMapping("/recent")
    suspend fun listRecentEvents(
        @RequestParam(defaultValue = "10") limit: Int
    ): List<EventDto> =
        eventService.listRecentEvents(limit)

    @GetMapping("/{id}")
    suspend fun getEvent(@PathVariable id: UUID): EventDto =
        eventService.getEvent(id)

    @GetMapping("/device/{deviceId}/count")
    suspend fun getEventCountByDevice(
        @PathVariable deviceId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): Long {
        if (!deviceService.hasAccess(deviceId, principal.id)) {
            throw AccessDeniedException("No access to device")
        }
        return eventService.getEventCountByDevice(deviceId)
    }

    @PostMapping(value = ["/bell-ring"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun handleBellRing(
        @RequestParam("image") image: MultipartFile,
        @RequestParam("device_id") deviceIdStr: String,
        @RequestParam("device_key") deviceKey: String
    ): EventDto {
        val device = deviceRepository.findByDeviceId(deviceIdStr)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown device")

        if (!passwordEncoder.matches(deviceKey, device.deviceKey)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device key")
        }

        logger.info("Bell ring with image received from device: $deviceIdStr")

        // 1. Create the event
        val event = eventService.createEvent(EventCreateRequest(
            deviceId = device.id!!,
            eventType = EventType.DOORBELL_RING
        ))

        // 2. Save the image
        eventService.saveEventImage(event.id, image.bytes)

        // 3. Send notifications
        val usersWithAccess = userDeviceAccessRepository.findAllByDevice(device.id).map { it.user }.toList()
        if (usersWithAccess.isNotEmpty()) {
            notificationService.sendBroadcastNotification(
                userIds = usersWithAccess,
                title = "Doorbell Alert",
                body = "Someone is at the ${device.displayName}!"
            )
        }

        return eventService.getEvent(event.id)
    }
}
