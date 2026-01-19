package com.thomas.espdoorbell.doorbell.mqtt.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.thomas.espdoorbell.doorbell.mqtt.config.MqttProperties
import com.thomas.espdoorbell.doorbell.mqtt.model.BellAckMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.FactoryResetMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.SetChimeMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.SetNightModeMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.SetVolumeMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.StreamStartMessage
import com.thomas.espdoorbell.doorbell.mqtt.model.StreamStopMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.mqttv5.client.MqttClient
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID


@Service
class MqttPublisherService(
    private val mqttClient: MqttClient,
    private val mqttProperties: MqttProperties,
    private val mqttConnectionManager: MqttConnectionManager,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(MqttPublisherService::class.java)

    suspend fun publishStreamStart(deviceIdentifier: String, userId: UUID): Boolean {
        val message = StreamStartMessage(
            deviceId = deviceIdentifier,
            requestedBy = userId.toString()
        )

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.streamStart,
            deviceIdentifier
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    suspend fun publishStreamStop(deviceIdentifier: String): Boolean {
        val message = StreamStopMessage(deviceId = deviceIdentifier)

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.streamStop,
            deviceIdentifier
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    suspend fun publishSetChime(deviceIdentifier: String, chimeIndex: Int): Boolean {
        val message = SetChimeMessage(
            deviceId = deviceIdentifier,
            chimeIndex = chimeIndex
        )

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.settings,
            deviceIdentifier
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    suspend fun publishSetVolume(deviceIdentifier: String, volumeLevel: Int): Boolean {
        val message = SetVolumeMessage(
            deviceId = deviceIdentifier,
            volumeLevel = volumeLevel
        )

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.settings,
            deviceIdentifier
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    suspend fun publishSetNightMode(
        deviceIdentifier: String,
        enabled: Boolean,
        startTime: String,
        endTime: String
    ): Boolean {
        val message = SetNightModeMessage(
            deviceId = deviceIdentifier,
            nightModeEnabled = enabled,
            nightModeStart = startTime,
            nightModeEnd = endTime
        )

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.settings,
            deviceIdentifier
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    suspend fun publishFactoryReset(deviceIdentifier: String): Boolean {
        val message = FactoryResetMessage(deviceId = deviceIdentifier)

        val topic = mqttProperties.formatTopic(
            mqttProperties.topics.settings,
            deviceIdentifier
        )

        return publishMessage(topic, message, mqttProperties.qos.default, retained = false)
    }

    suspend fun publishBellAck(deviceIdentifier: String, eventId: UUID): Boolean {
        val message = BellAckMessage(
            deviceId = deviceIdentifier,
            eventId = eventId.toString()
        )

        val topic = "doorbell/$deviceIdentifier/bell-ack"

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
    
    suspend fun publishRaw(topic: String, payload: String, qos: Int = 1): Boolean {
        return try {
            if (!mqttConnectionManager.ensureConnected()) {
                logger.error("Cannot publish message - MQTT client not connected")
                return false
            }
            
            val mqttMessage = MqttMessage().apply {
                this.payload = payload.toByteArray()
                this.qos = qos
                isRetained = false
            }

            withContext(Dispatchers.IO) {
                mqttClient.publish(topic, mqttMessage)
            }
            
            logger.info("Published raw message to topic: $topic, qos: $qos")
            true
        } catch (e: Exception) {
            logger.error("Failed to publish raw message to topic: $topic", e)
            false
        }
    }
}


