package com.thomas.espdoorbell.doorbell.email.model

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
data class OTPData(
    val otp: String,
    val timestamp: Instant
)
