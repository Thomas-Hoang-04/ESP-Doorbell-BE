package com.thomas.espdoorbell.doorbell.utility

import jakarta.persistence.Embeddable
import jakarta.persistence.Column
import java.io.Serializable
import java.util.UUID

@Embeddable
data class UserDeviceAccessId(
    var userId: UUID,
    val deviceId: UUID,
): Serializable
