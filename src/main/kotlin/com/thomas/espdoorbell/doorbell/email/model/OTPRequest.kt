package com.thomas.espdoorbell.doorbell.email.model

data class OTPRequest(
    val email: String,
    val purpose: OTPPurpose
)
