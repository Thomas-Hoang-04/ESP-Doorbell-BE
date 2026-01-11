package com.thomas.espdoorbell.doorbell.mqtt.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.thomas.espdoorbell.doorbell.mqtt.config.MqttProperties
import com.thomas.espdoorbell.doorbell.mqtt.handler.DeviceHeartbeatHandler
import com.thomas.espdoorbell.doorbell.mqtt.handler.BellEventHandler
import com.thomas.espdoorbell.doorbell.mqtt.model.DeviceHeartbeatMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.BellEventMessage
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for subscribing to MQTT topics and processing incoming messages.
 * 
 * Registers handlers with MqttConnectionManager rather than setting its own callback
 * to avoid callback conflicts.
 */
@Service
class MqttSubscriberService(
    private val mqttClient: MqttClient,
    private val mqttProperties: MqttProperties,
    private val mqttConnectionManager: MqttConnectionManager,
    private val deviceHeartbeatHandler: DeviceHeartbeatHandler,
    private val bellEventHandler: BellEventHandler,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MqttSubscriberService::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @PostConstruct
    fun initialize() {
        logger.info("Initializing MQTT Subscriber Service")
        
        mqttConnectionManager.registerMessageHandler { topic, message ->
            handleMessage(topic, message)
        }
        
        mqttConnectionManager.registerOnConnectCallback { reconnect, serverURI ->
            logger.info("Connection callback triggered. Reconnect: $reconnect, Server: $serverURI")
            subscribeToTopics()
        }
    }

    @PreDestroy
    fun shutdown() {
        logger.info("Shutting down MQTT Subscriber Service")
        job.cancel()
        scope.cancel()
    }

    private fun subscribeToTopics() {
        try {
            if (!mqttConnectionManager.isConnected()) {
                logger.warn("MQTT client not connected, cannot subscribe to topics")
                return
            }

            val heartbeatTopic = mqttProperties.topics.heartbeat
            mqttClient.subscribe(heartbeatTopic, mqttProperties.qos.heartbeat)
            logger.info("Subscribed to heartbeat topic: $heartbeatTopic")

            val bellEventTopic = mqttProperties.topics.bellEvent
            mqttClient.subscribe(bellEventTopic, mqttProperties.qos.bellEvent)
            logger.info("Subscribed to bell event topic: $bellEventTopic")

        } catch (e: MqttException) {
            logger.error("Failed to subscribe to topics", e)
        }
    }

    private fun handleMessage(topic: String, message: MqttMessage) {
        scope.launch {
            try {
                processMessage(topic, message)
            } catch (e: Exception) {
                logger.error("Error processing message from topic: $topic", e)
            }
        }
    }

    private suspend fun processMessage(topic: String, message: MqttMessage) {
        val payload = String(message.payload)
        logger.debug("Received message on topic '$topic': $payload")

        when {
            topic.contains("/heartbeat") -> processHeartbeatMessage(payload)
            topic.contains("/event/bell") -> processBellEventMessage(payload)
            else -> logger.warn("Unknown topic pattern: $topic")
        }
    }

    private suspend fun processHeartbeatMessage(payload: String) {
        try {
            val heartbeatMessage = objectMapper.readValue(payload, DeviceHeartbeatMessage::class.java)
            deviceHeartbeatHandler.handleHeartbeat(heartbeatMessage)
        } catch (e: Exception) {
            logger.error("Failed to parse heartbeat message: $payload", e)
        }
    }

    private suspend fun processBellEventMessage(payload: String) {
        try {
            val bellEventMessage = objectMapper.readValue(payload, BellEventMessage::class.java)
            bellEventHandler.handleBellEvent(bellEventMessage)
        } catch (e: Exception) {
            logger.error("Failed to parse bell event message: $payload", e)
        }
    }
}




