package com.thomas.espdoorbell.doorbell.mqtt.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.thomas.espdoorbell.doorbell.intercom.IntercomIceService
import com.thomas.espdoorbell.doorbell.mqtt.config.MqttProperties
import com.thomas.espdoorbell.doorbell.mqtt.handler.BellEventHandler
import com.thomas.espdoorbell.doorbell.mqtt.handler.DeviceHeartbeatHandler
import com.thomas.espdoorbell.doorbell.mqtt.model.BellEventMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.DeviceHeartbeatMessage
import com.thomas.espdoorbell.doorbell.streaming.ice.StreamingSessionManager
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service


@Service
class MqttSubscriberService(
    private val mqttClient: MqttClient,
    private val mqttProperties: MqttProperties,
    private val mqttConnectionManager: MqttConnectionManager,
    private val deviceHeartbeatHandler: DeviceHeartbeatHandler,
    private val bellEventHandler: BellEventHandler,
    private val streamingSessionManager: StreamingSessionManager,
    private val intercomIceService: IntercomIceService,
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
            
            val iceOfferTopic = mqttProperties.topics.iceOffer
            mqttClient.subscribe(iceOfferTopic, mqttProperties.qos.default)
            logger.info("Subscribed to ICE offer topic: $iceOfferTopic")
            
            val intercomOfferTopic = "doorbell/+/intercom/offer"
            mqttClient.subscribe(intercomOfferTopic, mqttProperties.qos.default)
            logger.info("Subscribed to intercom offer topic: $intercomOfferTopic")

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
            topic.contains("/ice/offer") -> processIceOfferMessage(topic, payload)
            topic.contains("/intercom/offer") -> processIntercomOfferMessage(topic, payload)
            else -> logger.warn("Unknown topic pattern: $topic")
        }
    }
    
    private fun processIceOfferMessage(topic: String, payload: String) {
        val deviceIdentifier = extractDeviceIdFromTopic(topic) ?: return
        try {
            // ICE offer payload from ESP32 is raw SDP string
            val sdp = payload
            streamingSessionManager.updateSession(deviceIdentifier, sdp, null)
            logger.info("Processed ICE offer from device: {}", deviceIdentifier)
        } catch (e: Exception) {
            logger.error("Failed to process ICE offer from {}: {}", deviceIdentifier, e.message)
        }
    }
    
    private fun processIntercomOfferMessage(topic: String, payload: String) {
        val deviceIdentifier = extractDeviceIdFromTopic(topic)
        if (deviceIdentifier != null) {
            try {
                val json = objectMapper.readTree(payload)
                val sdp = json.get("sdp")?.asText() ?: ""
                val candidates = json.get("candidates")?.map { it.asText() } ?: emptyList()
                intercomIceService.handleEspOffer(deviceIdentifier, sdp, candidates)
                logger.info("Processed intercom offer from device: {}", deviceIdentifier)
            } catch (e: Exception) {
                logger.error("Failed to parse intercom offer from {}: {}", deviceIdentifier, e.message)
            }
        }
    }
    
    private fun extractDeviceIdFromTopic(topic: String): String? {
        val parts = topic.split("/")
        return if (parts.size >= 2 && parts[0] == "doorbell") parts[1] else null
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




