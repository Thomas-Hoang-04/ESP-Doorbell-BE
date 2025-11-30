package com.thomas.espdoorbell.doorbell.mqtt.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.thomas.espdoorbell.doorbell.mqtt.config.MqttProperties
import com.thomas.espdoorbell.doorbell.mqtt.handler.DeviceHeartbeatHandler
import com.thomas.espdoorbell.doorbell.mqtt.model.HeartbeatMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.StatusMessage
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties as PahoMqttProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for subscribing to MQTT topics and processing incoming messages
 */
@Service
class MqttSubscriberService(
    private val mqttClient: MqttClient,
    private val mqttProperties: MqttProperties,
    private val mqttConnectionManager: MqttConnectionManager,
    private val deviceHeartbeatHandler: DeviceHeartbeatHandler,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MqttSubscriberService::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @PostConstruct
    fun initialize() {
        logger.info("Initializing MQTT Subscriber Service")
        setupMessageCallback()
        // Subscription will happen automatically when connection completes
    }

    /**
     * Subscribe to all configured topics
     */
    private fun subscribeToTopics() {
        try {
            if (!mqttConnectionManager.isConnected()) {
                logger.warn("MQTT client not connected, cannot subscribe to topics")
                return
            }

            // Subscribe to heartbeat topic with wildcard for all devices
            val heartbeatTopic = mqttProperties.topics.heartbeat
            mqttClient.subscribe(heartbeatTopic, mqttProperties.qos.heartbeat)
            logger.info("Subscribed to heartbeat topic: $heartbeatTopic")

            // Subscribe to status topic with wildcard for all devices
            val statusTopic = mqttProperties.topics.status
            mqttClient.subscribe(statusTopic, mqttProperties.qos.default)
            logger.info("Subscribed to status topic: $statusTopic")

        } catch (e: MqttException) {
            logger.error("Failed to subscribe to topics", e)
        }
    }

    /**
     * Setup callback to handle incoming messages
     */
    private fun setupMessageCallback() {
        mqttClient.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic == null || message == null) {
                    logger.warn("Received null topic or message")
                    return
                }

                scope.launch {
                    try {
                        processMessage(topic, message)
                    } catch (e: Exception) {
                        logger.error("Error processing message from topic: $topic", e)
                    }
                }
            }

            override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
                logger.warn("MQTT subscriber disconnected: ${disconnectResponse?.reasonString}")
            }

            override fun mqttErrorOccurred(exception: MqttException?) {
                logger.error("MQTT error in subscriber", exception)
            }

            override fun deliveryComplete(token: IMqttToken?) {
                // Not used for subscriber
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                logger.info("MQTT subscriber connected. Reconnect: $reconnect, Server: $serverURI")
                // Subscribe to topics for BOTH initial connection AND reconnections
                subscribeToTopics()
            }

            override fun authPacketArrived(reasonCode: Int, properties: PahoMqttProperties?) {
                // Not used
            }
        })
    }

    /**
     * Process an incoming MQTT message based on topic
     */
    private suspend fun processMessage(topic: String, message: MqttMessage) {
        try {
            val payload = String(message.payload)
            logger.debug("Received message on topic '$topic': $payload")

            when {
                topic.contains("/heartbeat") -> {
                    processHeartbeatMessage(payload)
                }
                topic.contains("/status") -> {
                    processStatusMessage(payload)
                }
                else -> {
                    logger.warn("Unknown topic pattern: $topic")
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing message from topic: $topic", e)
        }
    }

    /**
     * Process heartbeat message
     */
    private suspend fun processHeartbeatMessage(payload: String) {
        try {
            val heartbeatMessage = objectMapper.readValue(payload, HeartbeatMessage::class.java)
            deviceHeartbeatHandler.handleHeartbeat(heartbeatMessage)
        } catch (e: Exception) {
            logger.error("Failed to parse heartbeat message: $payload", e)
        }
    }

    /**
     * Process status message
     */
    private suspend fun processStatusMessage(payload: String) {
        try {
            val statusMessage = objectMapper.readValue(payload, StatusMessage::class.java)
            logger.info(
                "Device status update: deviceId=${statusMessage.deviceId}, " +
                "active=${statusMessage.isActive}, firmware=${statusMessage.firmwareVersion}"
            )
            
            // Could add additional processing for status messages here
            // For now, just log them
            
        } catch (e: Exception) {
            logger.error("Failed to parse status message: $payload", e)
        }
    }
}

