package com.thomas.espdoorbell.doorbell.mqtt.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "mqtt")
data class MqttProperties(
    val broker: BrokerSettings,
    val client: ClientSettings,
    val topics: TopicSettings,
    val qos: QosSettings,
) {
    private val logger = LoggerFactory.getLogger(MqttProperties::class.java)

    data class BrokerSettings(
        val url: String = "tcp://localhost",
        val port: Int = 1883,
        val username: String,
        val password: String,
        val ssl: SslSettings? = null,
    )

    data class SslSettings(
        val enabled: Boolean = false,
        val caPath: String? = null,
        val clientCertPath: String? = null,
        val clientKeyPath: String? = null,
    )

    data class ClientSettings(
        val id: String,
        val autoReconnect: Boolean = true,
        val cleanSession: Boolean = false,
        val connectionTimeout: Int = 30,
        val keepAliveInterval: Int = 60
    )

    data class TopicSettings(
        val prefix: String,
        val streamStart: String,
        val streamStop: String,
        val heartbeat: String,
        val bellEvent: String,
    )

    data class QosSettings(
        val default: Int = 1,
        val heartbeat: Int = 0,
        val bellEvent: Int = 1
    )

    val brokerUrl: String
        get() = "${broker.url}:${broker.port}"

    fun formatTopic(template: String, deviceId: String): String =
        template.replace("{deviceId}", deviceId)

    @PostConstruct
    fun validate() {
        require(broker.url.isNotBlank()) { "MQTT broker URL must not be blank" }
        require(broker.port in 1..65535) { "MQTT broker port must be between 1 and 65535" }
        require(client.id.isNotBlank()) { "MQTT client ID must not be blank" }
        require(qos.default in 0..2) { "MQTT QoS must be between 0 and 2" }
        require(qos.heartbeat in 0..2) { "MQTT heartbeat QoS must be between 0 and 2" }
        
        logger.info("MQTT properties validated: broker=$brokerUrl, clientId=${client.id}")
    }
}


