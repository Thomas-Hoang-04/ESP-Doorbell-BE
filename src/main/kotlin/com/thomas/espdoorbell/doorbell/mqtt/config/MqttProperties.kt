package com.thomas.espdoorbell.doorbell.mqtt.config

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
        var heartbeat: String = "doorbell/+/heartbeat",
        var status: String = "doorbell/+/status"
    )

    data class QosSettings(
        var default: Int = 1,
        var heartbeat: Int = 0
    )

    /**
     * Get the full broker URL with port
     */
    val brokerURl: String = "${broker.url}:${broker.port}"

    /**
     * Format a topic with device ID
     */
    fun formatTopic(template: String, deviceId: String): String {
        return template.replace("{deviceId}", deviceId)
    }
}

