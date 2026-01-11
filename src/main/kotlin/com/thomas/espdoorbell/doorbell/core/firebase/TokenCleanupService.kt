package com.thomas.espdoorbell.doorbell.core.firebase

import com.thomas.espdoorbell.doorbell.user.repository.UserFcmTokenRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class TokenCleanupService(
    private val userFcmTokenRepository: UserFcmTokenRepository
) {
    private val logger = LoggerFactory.getLogger(TokenCleanupService::class.java)

    @Scheduled(cron = "0 0 0 * * *")
    fun cleanupStaleTokens() = runBlocking {
        val threshold = OffsetDateTime.now().minusDays(30)
        logger.info("Running FCM token cleanup for users inactive since $threshold")

        val deletedCount = userFcmTokenRepository.deleteByInactiveUsersBefore(threshold)
        logger.info("Cleaned up $deletedCount FCM tokens")
    }
}
