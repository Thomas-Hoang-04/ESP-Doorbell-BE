package com.thomas.espdoorbell.doorbell.mqtt.service

import com.thomas.espdoorbell.doorbell.mqtt.config.MqttProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.delay
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties as PahoMqttProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages MQTT connection lifecycle including reconnection strategy
 */
@Service
class MqttConnectionManager(
    private val mqttClient: MqttClient,
    private val mqttConnectionOptions: MqttConnectionOptions,
    private val mqttProperties: MqttProperties
) {
    private val logger = LoggerFactory.getLogger(MqttConnectionManager::class.java)

    @Volatile
    private var isConnected = false

    @Volatile
    private var reconnectAttempts = 0

    private val maxReconnectAttempts = 10
    private val baseReconnectDelay = 1000L // 1 second

    @PostConstruct
    fun initialize() {
        logger.info("Initializing MQTT Connection Manager")
        setupCallbacks()
        
        // Retry initial connection with exponential backoff
        var attempts = 0
        while (attempts < maxReconnectAttempts) {
            try {
                connect()
                if (mqttClient.isConnected) {
                    logger.info("MQTT initial connection successful")
                    return
                }
            } catch (e: Exception) {
                attempts++
                if (attempts < maxReconnectAttempts) {
                    val delay = calculateBackoff(attempts - 1)
                    logger.warn(
                        "Initial connection failed (attempt $attempts/$maxReconnectAttempts), " +
                        "retrying in ${delay}ms", e
                    )
                    Thread.sleep(delay)
                } else {
                    logger.error(
                        "Failed to establish initial MQTT connection after $maxReconnectAttempts attempts. " +
                        "MQTT features will be unavailable.", e
                    )
                }
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down MQTT Connection Manager")
        disconnect()
    }

    /**
     * Ensure the client is connected, attempt reconnecting if not
     */
    suspend fun ensureConnected(): Boolean {
        if (isConnected && mqttClient.isConnected) {
            return true
        }

        return reconnect()
    }

    /**
     * Get the current connection status
     */
    fun isConnected(): Boolean = isConnected && mqttClient.isConnected

    /**
     * Connect to MQTT broker
     * Throws MqttException if connection fails
     */
    private fun connect() {
        if (mqttClient.isConnected) {
            logger.info("MQTT client already connected")
            isConnected = true
            return
        }

        logger.info("Connecting to MQTT broker: ${mqttProperties.brokerURl}")
        mqttClient.connect(mqttConnectionOptions)
        
        isConnected = true
        reconnectAttempts = 0
        logger.info("Successfully connected to MQTT broker")
    }

    /**
     * Disconnect from MQTT broker
     */
    private fun disconnect() {
        try {
            if (mqttClient.isConnected) {
                logger.info("Disconnecting from MQTT broker")
                mqttClient.disconnect()
                isConnected = false
                logger.info("Disconnected from MQTT broker")
            }
        } catch (e: MqttException) {
            logger.error("Error disconnecting from MQTT broker", e)
        }
    }

    /**
     * Reconnect with exponential backoff
     */
    private suspend fun reconnect(): Boolean {
        if (reconnectAttempts >= maxReconnectAttempts) {
            logger.error("Max reconnect attempts ($maxReconnectAttempts) reached. Giving up.")
            return false
        }

        val delay = calculateBackoff(reconnectAttempts)
        logger.info("Attempting reconnect in ${delay}ms (attempt ${reconnectAttempts + 1}/$maxReconnectAttempts)")
        
        delay(delay)
        reconnectAttempts++

        try {
            if (!mqttClient.isConnected) {
                mqttClient.connect(mqttConnectionOptions)
                isConnected = true
                reconnectAttempts = 0
                logger.info("Reconnected successfully to MQTT broker")
                return true
            }
            return true
        } catch (e: MqttException) {
            logger.error("Reconnect attempt $reconnectAttempts failed", e)
            return false
        }
    }

    /**
     * Calculate exponential backoff delay
     */
    private fun calculateBackoff(attempt: Int): Long {
        val exponentialDelay = baseReconnectDelay * (2.0.pow(attempt).toLong())
        val maxDelay = 60000L // 1-minute max
        return min(exponentialDelay, maxDelay)
    }

    /**
     * Set up MQTT callbacks for connection events
     */
    private fun setupCallbacks() {
        mqttClient.setCallback(object : MqttCallback {
            override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
                isConnected = false
                logger.warn("MQTT client disconnected: ${disconnectResponse?.reasonString}")
                
                // Attempt automatic reconnection if configured
                if (mqttProperties.client.autoReconnect) {
                    logger.info("Initiating automatic reconnection")
                }
            }

            override fun mqttErrorOccurred(exception: MqttException?) {
                logger.error("MQTT error occurred", exception)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // This is handled by MqttSubscriberService
                // Keeping this empty to satisfy the interface
            }

            override fun deliveryComplete(token: IMqttToken?) {
                logger.debug("Message delivery complete")
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                isConnected = true
                reconnectAttempts = 0
                logger.info("MQTT connection complete. Reconnect: $reconnect, Server: $serverURI")
            }

            override fun authPacketArrived(reasonCode: Int, properties: PahoMqttProperties?) {
                logger.debug("Auth packet arrived with reason code: $reasonCode")
            }
        })
    }
}

