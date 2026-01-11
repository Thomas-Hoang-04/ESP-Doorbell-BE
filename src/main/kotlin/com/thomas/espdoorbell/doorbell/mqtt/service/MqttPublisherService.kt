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

    suspend fun publishStreamStart(deviceId: UUID, userId: UUID): Boolean {
        val message = StreamStartMessage(
            deviceId = deviceId.toString(),
            requestedBy = userId.toString()
        )

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.streamStart,
            deviceId.toString()
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    suspend fun publishStreamStop(deviceId: UUID): Boolean {
        val message = StreamStopMessage(deviceId = deviceId.toString())

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.streamStop,
            deviceId.toString()
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    @Suppress("SameParameterValue")
    private suspend fun publishMessage(topic: String, message: Any, qos: Int, retained: Boolean): Boolean {
        return try {
            if (!mqttConnectionManager.ensureConnected()) {
                logger.error("Cannot publish message - MQTT client not connected")
                return false
            }

            val jsonPayload = objectMapper.writeValueAsString(message)
            
            val mqttMessage = MqttMessage().apply {
                payload = jsonPayload.toByteArray()
                this.qos = qos
                isRetained = retained
            }

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


