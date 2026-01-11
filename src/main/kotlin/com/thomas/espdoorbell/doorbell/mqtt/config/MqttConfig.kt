package com.thomas.espdoorbell.doorbell.mqtt.config

import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

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
            val client = MqttClient(brokerUrl, clientId, MemoryPersistence())

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

        options.isAutomaticReconnect = mqttProperties.client.autoReconnect
        options.isCleanStart = mqttProperties.client.cleanSession
        options.connectionTimeout = mqttProperties.client.connectionTimeout
        options.keepAliveInterval = mqttProperties.client.keepAliveInterval

        if (mqttProperties.broker.username.isNotEmpty()) {
            options.userName = mqttProperties.broker.username
            options.password = mqttProperties.broker.password.toByteArray()
        }

        mqttProperties.broker.ssl?.let { ssl ->
            if (ssl.enabled) {
                logger.info("Configuring MQTT SSL/TLS")
                options.socketFactory = createSslSocketFactory(ssl)
            }
        }

        logger.info(
            "MQTT connection options configured: autoReconnect=${options.isAutomaticReconnect}, " +
            "cleanStart=${options.isCleanStart}, timeout=${options.connectionTimeout}s, " +
            "keepAlive=${options.keepAliveInterval}s, ssl=${mqttProperties.broker.ssl?.enabled ?: false}"
        )

        return options
    }

    private fun createSslSocketFactory(ssl: MqttProperties.SslSettings): SSLSocketFactory {
        val caResource = ClassPathResource(ssl.caPath ?: "certs/ca.pem")
        val certFactory = CertificateFactory.getInstance("X.509")
        val caCert = caResource.inputStream.use { certFactory.generateCertificate(it) }

        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ca", caCert)
        }

        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagerFactory.trustManagers, null)

        logger.info("SSL socket factory created with CA from: ${ssl.caPath ?: "certs/ca.pem"}")
        return sslContext.socketFactory
    }
}
