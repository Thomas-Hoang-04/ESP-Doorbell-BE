package com.thomas.espdoorbell.doorbell.core.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val storage: Storage = Storage(),
    val api: Api = Api()
) {
    data class Storage(
        val uploadDir: String = "uploads"
    )

    data class Api(
        val baseUrl: String = "http://localhost:8080"
    )
}
