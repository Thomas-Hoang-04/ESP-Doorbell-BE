package com.thomas.espdoorbell.doorbell.service.device

import com.thomas.espdoorbell.doorbell.model.dto.device.DeviceDto
import com.thomas.espdoorbell.doorbell.model.dto.event.EventDto
import com.thomas.espdoorbell.doorbell.model.dto.user.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.model.entity.Devices
import com.thomas.espdoorbell.doorbell.repository.device.DeviceRepository
import com.thomas.espdoorbell.doorbell.repository.event.EventRepository
import com.thomas.espdoorbell.doorbell.repository.user.UserDeviceAccessRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val eventRepository: EventRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
) {

    @Transactional(readOnly = true)
    fun listDevices(): List<DeviceDto> = deviceRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    fun getDevice(deviceId: UUID): DeviceDto =
        deviceRepository.findByIdOrNull(deviceId)?.toDto()
            ?: throw EntityNotFoundException("Device with id $deviceId was not found")

    // TODO: Update HTTP request format here
    @Transactional
    fun registerDevice(device: Devices): DeviceDto = deviceRepository.save(device).toDto()

    @Transactional(readOnly = true)
    fun listDeviceAccess(deviceId: UUID): List<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByDeviceId(deviceId).map { it.toDto() }

    @Transactional(readOnly = true)
    fun listDeviceEvents(deviceId: UUID, optInNotification: Boolean): List<EventDto> =
        eventRepository.findAllByDeviceId(deviceId).map { it.toDto(optInNotification) }
}
