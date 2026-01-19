package com.thomas.espdoorbell.doorbell.streaming.ice

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class StreamingSession(
    val deviceId: UUID,
    val deviceIdentifier: String,
    val userId: UUID,
    var espSdp: String? = null,
    var espCandidates: List<String> = emptyList()
)

@Component
class StreamingSessionManager {
    private val sessions = ConcurrentHashMap<String, StreamingSession>()

    fun createSession(deviceId: UUID, deviceIdentifier: String, userId: UUID): StreamingSession {
        val session = StreamingSession(deviceId, deviceIdentifier, userId)
        sessions[deviceIdentifier] = session
        return session
    }

    fun getSession(deviceIdentifier: String): StreamingSession? = sessions[deviceIdentifier]

    fun hasSession(deviceIdentifier: String): Boolean = sessions.containsKey(deviceIdentifier)

    fun updateSession(deviceIdentifier: String, sdp: String?, candidates: List<String>?) {
        sessions[deviceIdentifier]?.let { session ->
            if (sdp != null) session.espSdp = sdp
            if (candidates != null) session.espCandidates = candidates
        }
    }

    fun endSession(deviceIdentifier: String) {
        sessions.remove(deviceIdentifier)
    }
}
