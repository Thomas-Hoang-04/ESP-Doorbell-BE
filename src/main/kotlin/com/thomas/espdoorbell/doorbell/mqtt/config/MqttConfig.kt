package com.thomas.espdoorbell.doorbell.mqtt.config

import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MqttConfig(
    private val mqttProperties: MqttProperties
) {
    private val logger = LoggerFactory.getLogger(MqttConfig::class.java)

    @Bean
    fun mqttClient(): MqttClient {
        val brokerUrl = mqttProperties.brokerUrl
        val clientId = mqttProperties.client.id

        logger.info("Initializing MQTT client with broker: $brokerUrl, clientId: $clientId")

        return try {
            val persistence = MemoryPersistence()
            val client = MqttClient(brokerUrl, clientId, persistence)

            logger.info("MQTT client created successfully")
            client
        } catch (e: MqttException) {
            logger.error("Failed to create MQTT client", e)
            throw RuntimeException("Failed to initialize MQTT client", e)
        }
    }

    @Bean
    fun mqttConnectionOptions(): MqttConnectionOptions {
        val options = MqttConnectionOptions()

        // Connection settings
        options.isAutomaticReconnect = mqttProperties.client.autoReconnect
        options.isCleanStart = mqttProperties.client.cleanSession
        options.connectionTimeout = mqttProperties.client.connectionTimeout
        options.keepAliveInterval = mqttProperties.client.keepAliveInterval

        // Authentication
        if (mqttProperties.broker.username.isNotEmpty()) {
            options.userName = mqttProperties.broker.username
            options.password = mqttProperties.broker.password.toByteArray()
        }

        logger.info(
            "MQTT connection options configured: autoReconnect=${options.isAutomaticReconnect}, " +
            "cleanStart=${options.isCleanStart}, timeout=${options.connectionTimeout}s, " +
            "keepAlive=${options.keepAliveInterval}s"
        )

        return options
    }
}

