package com.thomas.espdoorbell.doorbell.event.controller

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.event.service.EventService
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventService: EventService,
    private val deviceService: DeviceService
) {
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
}
