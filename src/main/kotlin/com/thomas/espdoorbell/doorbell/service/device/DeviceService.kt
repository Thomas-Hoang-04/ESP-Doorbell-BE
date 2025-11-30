package com.thomas.espdoorbell.doorbell.service.device

import com.thomas.espdoorbell.doorbell.model.dto.device.DeviceDto
import com.thomas.espdoorbell.doorbell.model.dto.user.UserDeviceAccessDto
import com.thomas.espdoorbell.doorbell.model.entity.device.Devices
import com.thomas.espdoorbell.doorbell.model.request.DeviceRegister
import com.thomas.espdoorbell.doorbell.repository.device.DeviceRepository
import com.thomas.espdoorbell.doorbell.repository.user.UserDeviceAccessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.data.relational.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class DeviceService(
    private val deviceRepository: DeviceRepository,
    private val userDeviceAccessRepository: UserDeviceAccessRepository,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate
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

    // TODO: implement device status update

    /**
     * Update device online status
     */
    @Transactional
    suspend fun updateDeviceOnlineStatus(deviceId: UUID, isOnline: Boolean) {
        val query = Query.query(Criteria.where("id").`is`(deviceId))
        val update = Update.update("last_online", OffsetDateTime.now())
            .set("is_active", isOnline)
        
        r2dbcEntityTemplate.update(query, update, Devices::class.java)
    }

    /**
     * Update device metrics (battery level and signal strength)
     */
    @Transactional
    suspend fun updateDeviceMetrics(deviceId: UUID, batteryLevel: Int?, signalStrength: Int?) {
        val query = Query.query(Criteria.where("id").`is`(deviceId))
        val update = Update.update("last_online", OffsetDateTime.now())
        
        batteryLevel?.let { update.set("battery_level", it) }
        signalStrength?.let { update.set("signal_strength", it) }
        
        r2dbcEntityTemplate.update(query, update, Devices::class.java)
    }
}
