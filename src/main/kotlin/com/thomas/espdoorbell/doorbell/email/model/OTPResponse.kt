package com.thomas.espdoorbell.doorbell.email.model

data class OTPResponse(
    val status: OTPStatus,
    val message: String
)
