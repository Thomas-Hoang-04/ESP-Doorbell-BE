package com.thomas.espdoorbell.doorbell.service.device

import com.thomas.espdoorbell.doorbell.model.dto.device.DeviceDto
import com.thomas.espdoorbell.doorbell.model.dto.event.EventDto
import com.thomas.espdoorbell.doorbell.model.dto.user.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.model.request.DeviceRegister
import com.thomas.espdoorbell.doorbell.repository.device.DeviceRepository
import com.thomas.espdoorbell.doorbell.repository.event.EventRepository
import com.thomas.espdoorbell.doorbell.repository.user.UserDeviceAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
) {
    fun listDevices(): Flow<DeviceDto> = deviceRepository.findAll().map { it.toDto() }

    @Transactional(readOnly = true)
    suspend fun getDevice(deviceId: UUID): DeviceDto =
        deviceRepository.findById(deviceId)?.toDto()
            ?: throw IllegalArgumentException("Device with id $deviceId was not found")

    @Transactional
    suspend fun registerDevice(device: DeviceRegister): DeviceDto =
        deviceRepository.save(device.toEntity()).toDto()

    suspend fun listDeviceAccess(deviceId: UUID): Flow<UserDeviceAccessDto> =
        userDeviceAccessRepository.findAllByDeviceId(deviceId).map { it.toDto() }
}
