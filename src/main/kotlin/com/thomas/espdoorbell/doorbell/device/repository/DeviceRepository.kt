@file:Suppress(
    "unused"
)

package com.thomas.espdoorbell.doorbell.device.repository

import com.thomas.espdoorbell.doorbell.device.entity.Devices
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Suppress("unused")
@Repository
interface DeviceRepository : CoroutineCrudRepository<Devices, UUID> {
    
    suspend fun findByDeviceId(deviceId: String): Devices?

    fun findAllByIsActiveTrue(): Flow<Devices>

    @Query("SELECT * FROM devices WHERE last_online > NOW() - INTERVAL '1 minute' * :minutes")
    fun findOnlineWithin(@Param("minutes") minutes: Int): Flow<Devices>

    @Query("SELECT * FROM devices WHERE battery_level < :threshold")
    fun findByBatteryLevelLessThan(@Param("threshold") threshold: Int): Flow<Devices>

    @Query("SELECT EXISTS(SELECT 1 FROM devices WHERE device_id = :identifier)")
    suspend fun existsByIdentifier(@Param("identifier") identifier: String): Boolean

    @Query("SELECT * FROM devices WHERE id = :id AND device_key = :deviceKey")
    @Suppress("unused")
    suspend fun findByIdAndDeviceKey(@Param("id") id: UUID, @Param("deviceKey") deviceKey: String): Devices?
}

