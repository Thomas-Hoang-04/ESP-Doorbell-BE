package com.thomas.espdoorbell.doorbell.intercom

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.ice")
data class IceProperties(
    val turnHost: String = "",
    val turnPort: Int = 3478,
    val turnUsername: String = "",
    val turnPassword: String = ""
)
