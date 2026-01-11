package com.thomas.espdoorbell.doorbell.mqtt.service

import com.thomas.espdoorbell.doorbell.mqtt.config.MqttProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private lateinit var messageHandler: (String, MqttMessage) -> Unit
    private lateinit var onConnectCallback: (Boolean, String?) -> Unit

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PostConstruct
    fun initialize() {
        logger.info("Initializing MQTT Connection Manager")
        setupCallbacks()
        scope.launch { connectWithRetry() }
    }

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down MQTT Connection Manager")
        disconnect()
    }

    fun registerMessageHandler(handler: (topic: String, message: MqttMessage) -> Unit) {
        this.messageHandler = handler
        logger.debug("Message handler registered")
    }

    fun registerOnConnectCallback(callback: (reconnect: Boolean, serverURI: String?) -> Unit) {
        this.onConnectCallback = callback
        logger.debug("On-connect callback registered")
        
        if (isConnected && mqttClient.isConnected)
            callback(false, mqttProperties.brokerUrl)
    }

    suspend fun ensureConnected(): Boolean
        = (isConnected && mqttClient.isConnected) || reconnect()


    fun isConnected(): Boolean = isConnected && mqttClient.isConnected

    private suspend fun connectWithRetry() = withContext(Dispatchers.IO) {
        repeat(MAX_RECONNECT_ATTEMPTS) { times ->
            try {
                connect()
                if (mqttClient.isConnected) {
                    logger.info("MQTT initial connection successful")
                    return@withContext
                }
            } catch (e: Exception) {
                if (times + 1 < MAX_RECONNECT_ATTEMPTS) {
                    val delayMs = calculateBackoff(times)
                    logger.warn(
                        "Initial connection failed (attempt ${times + 1}/$MAX_RECONNECT_ATTEMPTS), " +
                        "retrying in ${delayMs}ms", e
                    )
                    delay(delayMs)
                } else
                    logger.error(
                        "Failed to establish initial MQTT connection after $MAX_RECONNECT_ATTEMPTS attempts. " +
                        "MQTT features will be unavailable.", e
                    )
            }
        }
    }

    private suspend fun connect() {
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
        assert(::messageHandler.isInitialized) { "Message handler must be initialized before setting up callbacks" }
        assert(::onConnectCallback.isInitialized) { "On-connect callback must be initialized before setting up callbacks" }
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
                    messageHandler.invoke(topic, message)
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
                onConnectCallback.invoke(reconnect, serverURI)
            }

            override fun authPacketArrived(reasonCode: Int, properties: PahoMqttProperties?) {
                logger.debug("Auth packet arrived with reason code: $reasonCode")
            }
        })
    }
}



