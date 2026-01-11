package com.thomas.espdoorbell.doorbell.email.model

data class OTPValidationRequest (
    val email: String,
    val otp: String,
)