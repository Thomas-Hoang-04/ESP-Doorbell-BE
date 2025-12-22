package com.thomas.espdoorbell.doorbell.user.entity

import com.thomas.espdoorbell.doorbell.shared.types.DeviceAccess
import com.thomas.espdoorbell.doorbell.shared.types.UserDeviceRole
import com.thomas.espdoorbell.doorbell.user.dto.UserDeviceAccessDto
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

@Table(name = "user_device_access")
class UserDeviceAccess(
    @Column("id")
    @Id
    @Suppress("unused")
    private val id: UUID = UUID.randomUUID(),

    @Column("user_id")
    val user: UUID,

    @Column("device_id")
    val device: UUID,

    @Column("role")
    private val role: UserDeviceRole = UserDeviceRole.MEMBER,

    @Column("updated_at")
    @LastModifiedDate
    private val updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_by")
    @LastModifiedBy
    private val updatedBy: UUID? = null,

    @Column("granted_status")
    private val accessStatus: DeviceAccess = DeviceAccess.GRANTED,
) {
    val springAuthority: String
        get() = role.toSpringAuthority()

    fun toDto(username: String? = null): UserDeviceAccessDto = UserDeviceAccessDto(
        userId = user,
        deviceId = device,
        roleCode = role.name,
        roleLabel = role.toDisplayName(),
        accessStatusCode = accessStatus.name,
        accessStatusLabel = accessStatus.toDisplayName(),
        updatedAt = updatedAt,
        updatedByUserId = updatedBy,
        updatedByUsername = username
    )
}