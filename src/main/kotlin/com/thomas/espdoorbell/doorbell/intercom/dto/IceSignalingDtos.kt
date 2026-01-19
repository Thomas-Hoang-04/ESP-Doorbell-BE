package com.thomas.espdoorbell.doorbell.intercom.dto

data class IceOfferResponse(
    val success: Boolean,
    val sdp: String? = null,
    val candidates: List<String>? = null,
    val message: String? = null
)

data class IceAnswerRequest(
    val sdp: String,
    val candidates: List<String>
)

data class IceConfigResponse(
    val turnHost: String,
    val turnPort: Int,
    val turnUsername: String,
    val turnPassword: String
)
