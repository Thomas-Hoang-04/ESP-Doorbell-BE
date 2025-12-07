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
 * Manages MQTT connection lifecycle and serves as the single callback owner.
 * 
 * This class owns the MqttCallback to avoid callback conflicts. Other services
 * can register listeners for specific events (message arrival, connection complete).
 */
@Service
class MqttConnectionManager(
    private val mqttClient: MqttClient,
    private val mqttConnectionOptions: MqttConnectionOptions,
    private val mqttProperties: MqttProperties
) {
    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val BASE_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 60_000L
    }

    private val logger = LoggerFactory.getLogger(MqttConnectionManager::class.java)

    @Volatile
    private var isConnected = false

    @Volatile
    private var reconnectAttempts = 0

    // Event listeners
    private var messageHandler: ((String, MqttMessage) -> Unit)? = null
    private var onConnectCallback: ((Boolean, String?) -> Unit)? = null

    @PostConstruct
    fun initialize() {
        logger.info("Initializing MQTT Connection Manager")
        setupCallbacks()
        connectWithRetry()
    }

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down MQTT Connection Manager")
        disconnect()
    }

    /**
     * Register a handler for incoming MQTT messages.
     * Only one handler can be registered at a time.
     */
    fun registerMessageHandler(handler: (topic: String, message: MqttMessage) -> Unit) {
        this.messageHandler = handler
        logger.debug("Message handler registered")
    }

    /**
     * Register a callback for connection complete events.
     * Used by subscriber service to know when to subscribe to topics.
     */
    fun registerOnConnectCallback(callback: (reconnect: Boolean, serverURI: String?) -> Unit) {
        this.onConnectCallback = callback
        logger.debug("On-connect callback registered")
        
        // If already connected, trigger the callback immediately
        if (isConnected && mqttClient.isConnected) {
            callback(false, mqttProperties.brokerUrl)
        }
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

    private fun connectWithRetry() {
        var attempts = 0
        while (attempts < MAX_RECONNECT_ATTEMPTS) {
            try {
                connect()
                if (mqttClient.isConnected) {
                    logger.info("MQTT initial connection successful")
                    return
                }
            } catch (e: Exception) {
                attempts++
                if (attempts < MAX_RECONNECT_ATTEMPTS) {
                    val delayMs = calculateBackoff(attempts - 1)
                    logger.warn(
                        "Initial connection failed (attempt $attempts/$MAX_RECONNECT_ATTEMPTS), " +
                        "retrying in ${delayMs}ms", e
                    )
                    Thread.sleep(delayMs)
                } else {
                    logger.error(
                        "Failed to establish initial MQTT connection after $MAX_RECONNECT_ATTEMPTS attempts. " +
                        "MQTT features will be unavailable.", e
                    )
                }
            }
        }
    }

    private fun connect() {
        if (mqttClient.isConnected) {
            logger.debug("MQTT client already connected")
            isConnected = true
            return
        }

        logger.info("Connecting to MQTT broker: ${mqttProperties.brokerUrl}")
        mqttClient.connect(mqttConnectionOptions)
        
        isConnected = true
        reconnectAttempts = 0
        logger.info("Successfully connected to MQTT broker")
    }

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

    private suspend fun reconnect(): Boolean {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            logger.error("Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached. Giving up.")
            return false
        }

        val delayMs = calculateBackoff(reconnectAttempts)
        logger.info("Attempting reconnect in ${delayMs}ms (attempt ${reconnectAttempts + 1}/$MAX_RECONNECT_ATTEMPTS)")
        
        delay(delayMs)
        reconnectAttempts++

        return try {
            if (!mqttClient.isConnected) {
                mqttClient.connect(mqttConnectionOptions)
                isConnected = true
                reconnectAttempts = 0
                logger.info("Reconnected successfully to MQTT broker")
            }
            true
        } catch (e: MqttException) {
            logger.error("Reconnect attempt $reconnectAttempts failed", e)
            false
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val exponentialDelay = BASE_RECONNECT_DELAY_MS * (2.0.pow(attempt).toLong())
        return min(exponentialDelay, MAX_RECONNECT_DELAY_MS)
    }

    private fun setupCallbacks() {
        mqttClient.setCallback(object : MqttCallback {
            override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
                isConnected = false
                logger.warn("MQTT disconnected: ${disconnectResponse?.reasonString}")
                
                if (mqttProperties.client.autoReconnect) {
                    logger.info("Auto-reconnect is enabled, will attempt reconnection")
                }
            }

            override fun mqttErrorOccurred(exception: MqttException?) {
                logger.error("MQTT error occurred", exception)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic != null && message != null) {
                    messageHandler?.invoke(topic, message)
                } else {
                    logger.warn("Received null topic or message")
                }
            }

            override fun deliveryComplete(token: IMqttToken?) {
                logger.debug("Message delivery complete")
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                isConnected = true
                reconnectAttempts = 0
                logger.info("MQTT connected. Reconnect: $reconnect, Server: $serverURI")
                onConnectCallback?.invoke(reconnect, serverURI)
            }

            override fun authPacketArrived(reasonCode: Int, properties: PahoMqttProperties?) {
                logger.debug("Auth packet arrived with reason code: $reasonCode")
            }
        })
    }
}



