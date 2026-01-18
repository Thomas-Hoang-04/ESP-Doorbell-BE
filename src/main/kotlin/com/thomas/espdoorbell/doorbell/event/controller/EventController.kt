package com.thomas.espdoorbell.doorbell.event.controller

import com.thomas.espdoorbell.doorbell.device.repository.DeviceRepository
import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.event.dto.EventDto
import com.thomas.espdoorbell.doorbell.event.service.EventService
import com.thomas.espdoorbell.doorbell.shared.principal.UserPrincipal
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.codec.multipart.FilePart
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.web.bind.annotation.*
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

    @PostMapping(value = ["/upload-image"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun uploadEventImage(
        @RequestPart("image") image: FilePart,
        @RequestPart("device_id") deviceId: String,
        @RequestPart("device_key") deviceKey: String,
        @RequestPart("event_id") eventIdStr: String
    ): EventDto {
        val device = deviceRepository.findByDeviceId(deviceId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown device")

        if (!passwordEncoder.matches(deviceKey, device.deviceKey)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device key")
        }

        val eventId = try {
            UUID.fromString(eventIdStr)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event_id format")
        }

        logger.info("Image upload for event $eventId from device: $deviceId")

        val dataBuffer = DataBufferUtils.join(image.content()).awaitSingle()
        val imageBytes = ByteArray(dataBuffer.readableByteCount())
        dataBuffer.read(imageBytes)
        DataBufferUtils.release(dataBuffer)
        eventService.saveEventImage(eventId, imageBytes)

        return eventService.getEvent(eventId)
    }
}
