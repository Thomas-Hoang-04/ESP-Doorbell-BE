package com.thomas.espdoorbell.doorbell.user.repository

import com.thomas.espdoorbell.doorbell.user.entity.UserDeviceAccess
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserDeviceAccessRepository : CoroutineCrudRepository<UserDeviceAccess, UUID> {
    fun findAllByUser(user: UUID): Flow<UserDeviceAccess>

    fun findAllByDevice(device: UUID): Flow<UserDeviceAccess>

    fun findByDeviceAndUser(device: UUID, user: UUID): Flow<UserDeviceAccess>

    @Query("SELECT * FROM user_device_access WHERE user_id = ANY (:userId)")
    fun findAllByUserIds(@Param("userId") userId: List<UUID>): Flow<UserDeviceAccess>

    @Query("SELECT * FROM user_device_access WHERE device_id = ANY (:deviceId)")
    fun findAllByDeviceIds(@Param("deviceId") deviceId: List<UUID>): Flow<UserDeviceAccess>
}