package com.thomas.espdoorbell.doorbell.intercom

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class IntercomSessionManager {
    data class IntercomSession(
        val deviceId: UUID,
        val deviceIdentifier: String,
        val userId: UUID,
        var espSdp: String? = null,
        var espCandidates: MutableList<String> = mutableListOf(),
        var state: IntercomState = IntercomState.PENDING
    )
    
    enum class IntercomState {
        PENDING,
        ESP_OFFER_RECEIVED,
        ENDED
    }
    
    private val sessions = ConcurrentHashMap<String, IntercomSession>()
    
    fun createSession(deviceId: UUID, deviceIdentifier: String, userId: UUID): IntercomSession {
        val session = IntercomSession(
            deviceId = deviceId,
            deviceIdentifier = deviceIdentifier,
            userId = userId
        )
        sessions[deviceIdentifier] = session
        return session
    }
    
    fun getSession(deviceIdentifier: String): IntercomSession? = sessions[deviceIdentifier]
    
    fun setEspOffer(deviceIdentifier: String, sdp: String, candidates: List<String>) {
        sessions[deviceIdentifier]?.apply {
            espSdp = sdp
            espCandidates.addAll(candidates)
            state = IntercomState.ESP_OFFER_RECEIVED
        }
    }
    

    
    fun endSession(deviceIdentifier: String): IntercomSession? {
        return sessions.remove(deviceIdentifier)?.apply {
            state = IntercomState.ENDED
        }
    }
    
    fun hasSession(deviceIdentifier: String): Boolean = sessions.containsKey(deviceIdentifier)
}
