package com.thomas.espdoorbell.doorbell.intercom

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IntercomIceService(
    private val sessionManager: IntercomSessionManager
) {
    private val logger = LoggerFactory.getLogger(IntercomIceService::class.java)

    fun handleEspOffer(deviceIdentifier: String, sdp: String, candidates: List<String>) {
        sessionManager.getSession(deviceIdentifier) ?: run {
            logger.warn("No session found for device: {}", deviceIdentifier)
            return
        }
        
        sessionManager.setEspOffer(deviceIdentifier, sdp, candidates)
        logger.info("Received and stored ESP32 ICE offer for device: {}", deviceIdentifier)
    }
}
