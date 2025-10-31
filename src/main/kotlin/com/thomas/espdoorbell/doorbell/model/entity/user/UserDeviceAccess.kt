package com.thomas.espdoorbell.doorbell.model.entity.user

import com.thomas.espdoorbell.doorbell.model.entity.Devices
import com.thomas.espdoorbell.doorbell.model.types.DeviceAccess
import com.thomas.espdoorbell.doorbell.model.types.UserRole
import com.thomas.espdoorbell.doorbell.utility.UserDeviceAccessId
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.PostgreSQLEnumJdbcType
import java.time.OffsetDateTime

@Entity
@Table(name = "user_device_access")
class UserDeviceAccess(
    @EmbeddedId
    private val id: UserDeviceAccessId = UserDeviceAccessId(),

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private val user: UserCredentials,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("deviceId")
    @JoinColumn(name = "device_id", nullable = false)
    private val device: Devices,

    @Enumerated(value = EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "role", nullable = false)
    private val role: UserRole = UserRole.MEMBER,

    @Column(name = "updated_at")
    private val updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "id")
    private val updatedBy: UserCredentials? = null,

    @Enumerated(value = EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType::class)
    @Column(name = "granted_status", nullable = false)
    private val accessStatus: DeviceAccess = DeviceAccess.GRANTED,
)