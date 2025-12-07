package com.thomas.espdoorbell.doorbell.mqtt.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "mqtt")
data class MqttProperties(
    var broker: BrokerSettings = BrokerSettings(),
    var client: ClientSettings = ClientSettings(),
    var topics: TopicSettings = TopicSettings(),
    var qos: QosSettings = QosSettings()
) {
    private val logger = LoggerFactory.getLogger(MqttProperties::class.java)

    data class BrokerSettings(
        var url: String = "tcp://localhost",
        var port: Int = 1883,
        var username: String = "",
        var password: String = ""
    )

    data class ClientSettings(
        var id: String = "esp-doorbell-server",
        var autoReconnect: Boolean = true,
        var cleanSession: Boolean = false,
        var connectionTimeout: Int = 30,
        var keepAliveInterval: Int = 60
    )

    data class TopicSettings(
        var prefix: String = "doorbell",
        var streamStart: String = "doorbell/{deviceId}/stream/start",
        var streamStop: String = "doorbell/{deviceId}/stream/stop",
        var heartbeat: String = "doorbell/+/heartbeat"
    )

    data class QosSettings(
        var default: Int = 1,
        var heartbeat: Int = 1
    )

    /** Full broker URL with port */
    val brokerUrl: String
        get() = "${broker.url}:${broker.port}"

    /** Format a topic template with device ID */
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


