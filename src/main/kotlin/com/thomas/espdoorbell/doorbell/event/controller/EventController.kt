package com.thomas.espdoorbell.doorbell.event.controller

import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.event.service.EventService
import kotlinx.coroutines.flow.toList
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

@RestController
@RequestMapping("/api/events")
class EventController(
    private val eventService: EventService
) {

    // ========== READ OPERATIONS ==========
    // Note: Event creation/update are internal-only via MQTT handlers

    /**
     * List all events.
     */
    @GetMapping
    suspend fun listEvents(): List<EventDto> =
        eventService.listEvents()

    /**
     * List events for a specific device.
     */
    @GetMapping("/device/{deviceId}")
    suspend fun listEventsByDevice(@PathVariable deviceId: UUID): List<EventDto> =
        eventService.listEventsByDevice(deviceId)

    /**
     * List events within a date range.
     */
    @GetMapping("/range")
    suspend fun listEventsByDateRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: OffsetDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: OffsetDateTime
    ): List<EventDto> =
        eventService.listEventsByDateRange(start, end)

    /**
     * List recent events (default: last 10).
     */
    @GetMapping("/recent")
    suspend fun listRecentEvents(
        @RequestParam(defaultValue = "10") limit: Int
    ): List<EventDto> =
        eventService.listRecentEvents(limit)

    /**
     * List currently active streams.
     */
    @GetMapping("/streaming")
    suspend fun listActiveStreams(): List<EventDto> =
        eventService.listActiveStreams().toList()

    /**
     * Get a specific event by ID.
     */
    @GetMapping("/{id}")
    suspend fun getEvent(@PathVariable id: UUID): EventDto =
        eventService.getEvent(id)

    /**
     * Get event count for a device.
     */
    @GetMapping("/device/{deviceId}/count")
    suspend fun getEventCountByDevice(@PathVariable deviceId: UUID): Long =
        eventService.getEventCountByDevice(deviceId)
}
