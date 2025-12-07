package com.thomas.espdoorbell.doorbell.mqtt.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.thomas.espdoorbell.doorbell.mqtt.config.MqttProperties
import com.thomas.espdoorbell.doorbell.mqtt.model.StreamStartMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.StreamStopMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for publishing MQTT messages to ESP32 devices
 */
@Service
class MqttPublisherService(
    private val mqttClient: MqttClient,
    private val mqttProperties: MqttProperties,
    private val mqttConnectionManager: MqttConnectionManager,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MqttPublisherService::class.java)

    /**
     * Publish a stream start request to an ESP32 device
     */
    suspend fun publishStreamStart(deviceId: UUID, userId: UUID): Boolean {
        val message = StreamStartMessage(
            deviceId = deviceId.toString(),
            requestedBy = userId.toString()
        )

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.streamStart,
            deviceId.toString()
        )

        // retained=false to avoid ESP32 getting stale commands on reconnection
        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    /**
     * Publish a stream stop request to an ESP32 device
     */
    suspend fun publishStreamStop(deviceId: UUID): Boolean {
        val message = StreamStopMessage(
            deviceId = deviceId.toString()
        )

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.streamStop,
            deviceId.toString()
        )

        // retained=false to avoid ESP32 getting stale commands on reconnection
        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    /**
     * Publish a generic message object to a topic
     */
    private suspend fun publishMessage(topic: String, message: Any, qos: Int, retained: Boolean): Boolean {
        return try {
            // Ensure connection
            if (!mqttConnectionManager.ensureConnected()) {
                logger.error("Cannot publish message - MQTT client not connected")
                return false
            }

            // Serialize the message to JSON
            val jsonPayload = objectMapper.writeValueAsString(message)
            
            // Create an MQTT message
            val mqttMessage = MqttMessage().apply {
                payload = jsonPayload.toByteArray()
                this.qos = qos
                isRetained = retained
            }

            // Publish on IO dispatcher (mqttClient.publish is blocking)
            withContext(Dispatchers.IO) {
                mqttClient.publish(topic, mqttMessage)
            }
            
            logger.info("Published message to topic: $topic, qos: $qos")
            logger.debug("Message payload: $jsonPayload")
            
            true
        } catch (e: Exception) {
            logger.error("Failed to publish message to topic: $topic", e)
            false
        }
    }
}


