package com.thomas.espdoorbell.doorbell.repository.device

import com.thomas.espdoorbell.doorbell.model.entity.Devices
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface DeviceRepository : JpaRepository<Devices, UUID>
