package com.thomas.espdoorbell.doorbell.core.firebase

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.thomas.espdoorbell.doorbell.user.entity.UserFcmToken
import com.thomas.espdoorbell.doorbell.user.repository.UserFcmTokenRepository
import com.thomas.espdoorbell.doorbell.user.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class NotificationService(
    private val firebaseMessaging: FirebaseMessaging,
    private val userFcmTokenRepository: UserFcmTokenRepository,
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    @Transactional
    suspend fun saveToken(userId: UUID, token: String) {
        if (!userRepository.existsById(userId)) {
            logger.warn("Attempted to save FCM token for non-existent user $userId")
            return
        }
        val existing = userFcmTokenRepository.findByUserIdAndToken(userId, token)
        if (existing != null) {
            return
        }
        userFcmTokenRepository.save(
            UserFcmToken(
                userId = userId,
                token = token
            )
        )
        logger.info("Saved FCM token for user $userId")
    }

    @Suppress("unused")
    suspend fun sendNotification(
        userId: UUID,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        val tokens = userFcmTokenRepository.findByUserId(userId).toList()
        if (tokens.isEmpty()) {
            logger.warn("No FCM tokens found for user $userId")
            return 0
        }

        var successCount = 0
        for (fcmToken in tokens) {
            val success = sendToToken(fcmToken.token, title, body, data)
            if (success) {
                successCount++
            } else {
                userFcmTokenRepository.deleteByToken(fcmToken.token)
                logger.info("Removed invalid token for user $userId")
            }
        }
        return successCount
    }

    suspend fun sendBroadcastNotification(
        userIds: List<UUID>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        if (userIds.isEmpty()) return 0

        val tokens = userFcmTokenRepository.findByUserIdIn(userIds).toList()
        if (tokens.isEmpty()) {
            logger.warn("No FCM tokens found for users $userIds")
            return 0
        }

        val uniqueTokens = tokens.map { it.token }.toSet()

        var successCount = 0
        for (token in uniqueTokens) {
            val success = sendToToken(token, title, body, data)
            if (success) {
                successCount++
            } else {
                userFcmTokenRepository.deleteByToken(token)
                logger.info("Removed invalid token")
            }
        }
        logger.info("Sent broadcast notification to ${successCount}/${uniqueTokens.size} tokens")
        return successCount
    }

    private suspend fun sendToToken(
        token: String,
        title: String,
        body: String,
        data: Map<String, String>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = mutableMapOf("title" to title, "body" to body)
                if (data.isNotEmpty()) {
                    payload.putAll(data)
                }
                val message = Message.builder()
                    .setToken(token)
                    .putAllData(payload)
                    .build()

                firebaseMessaging.send(message)
                true
            } catch (e: FirebaseMessagingException) {
                logger.error("Failed to send FCM message: ${e.message}")
                false
            }
        }
    }
}
