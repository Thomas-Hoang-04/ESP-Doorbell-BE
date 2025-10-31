package com.thomas.espdoorbell.doorbell.repository.user

import com.thomas.espdoorbell.doorbell.model.entity.user.UserDeviceAccess
import com.thomas.espdoorbell.doorbell.utility.UserDeviceAccessId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserDeviceAccessRepository : JpaRepository<UserDeviceAccess, UserDeviceAccessId> {
    @Query("select * from user_device_access where user_id = :userId", nativeQuery = true)
    fun findAllByUserId(@Param("userId") userId: UUID): List<UserDeviceAccess>

    @Query("select * from user_device_access where device_id = :deviceId", nativeQuery = true)
    fun findAllByDeviceId(@Param("deviceId") deviceId: UUID): List<UserDeviceAccess>
}
