package com.thomas.espdoorbell.doorbell.repository.device

import com.thomas.espdoorbell.doorbell.model.entity.device.Devices
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeviceRepository : CoroutineCrudRepository<Devices, UUID> {
    @Query("SELECT * FROM devices WHERE device_id = :identifier")
    suspend fun findByIdentifier(identifier: String): Flow<Devices>
}
