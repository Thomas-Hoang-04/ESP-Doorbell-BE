package com.thomas.espdoorbell.doorbell.streaming.pipeline

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class DeviceStreamManager(
    private val config: StreamingProperties,
    private val healthTracker: PipelineHealthTracker
) {
    private val logger = LoggerFactory.getLogger(DeviceStreamManager::class.java)

    private val pipelines = ConcurrentHashMap<UUID, PipelineState>()
    private val pipelineLocks = ConcurrentHashMap<UUID, Mutex>()

    data class PipelineState(
        val pipeline: DeviceTranscodingPipeline,
        var inboundSessionId: String? = null,
        val outboundSessionIds: MutableSet<String> = mutableSetOf()
    )

    suspend fun startPipeline(deviceId: UUID): DeviceTranscodingPipeline {
        val mutex = pipelineLocks.computeIfAbsent(deviceId) { Mutex() }
        return mutex.withLock {
            pipelines[deviceId]?.pipeline ?: run {
                logger.info("Creating new pipeline for device $deviceId")
                val pipeline = DeviceTranscodingPipeline(
                    deviceId = deviceId,
                    config = config,
                    healthTracker = healthTracker
                )
                try {
                    pipeline.start()
                    pipelines[deviceId] = PipelineState(pipeline = pipeline)
                    pipeline
                } catch (e: Exception) {
                    logger.error("Failed to start pipeline for device $deviceId", e)
                    throw e
                }
            }
        }
    }

    suspend fun stopPipeline(deviceId: UUID) {
        val mutex = pipelineLocks[deviceId] ?: return
        mutex.withLock {
            pipelines.remove(deviceId)?.let { state ->
                logger.info("Stopping pipeline for device $deviceId")
                try {
                    state.pipeline.stop()
                } catch (e: Exception) {
                    logger.error("Error stopping pipeline for device $deviceId", e)
                }
            }
        }
        pipelineLocks.remove(deviceId)
    }

    suspend fun registerInbound(deviceId: UUID, sessionId: String) {
        val state = pipelines[deviceId]
        if (state == null) {
            logger.info("Starting pipeline for inbound connection from device $deviceId")
            startPipeline(deviceId)
            pipelines[deviceId]?.inboundSessionId = sessionId
        } else {
            if (state.inboundSessionId != null && state.inboundSessionId != sessionId) {
                throw IllegalStateException("Device $deviceId already has an active inbound connection")
            }
            state.inboundSessionId = sessionId
        }
        logger.info("Registered inbound session $sessionId for device $deviceId")
    }

    suspend fun unregisterInbound(deviceId: UUID, sessionId: String) {
        pipelines[deviceId]?.let { state ->
            if (state.inboundSessionId == sessionId) {
                state.inboundSessionId = null
                logger.info("Unregistered inbound session $sessionId for device $deviceId")

                if (state.outboundSessionIds.isEmpty()) {
                    logger.info("No outbound clients, stopping pipeline for device $deviceId")
                    stopPipeline(deviceId)
                }
            }
        }
    }

    fun registerOutbound(deviceId: UUID, sessionId: String) {
        pipelines[deviceId]?.let { state ->
            state.outboundSessionIds.add(sessionId)
            logger.info("Registered outbound session $sessionId for device $deviceId (total: ${state.outboundSessionIds.size})")
        } ?: run {
            logger.warn("Attempted to register outbound for device $deviceId but no pipeline exists")
        }
    }

    suspend fun unregisterOutbound(deviceId: UUID, sessionId: String) {
        pipelines[deviceId]?.let { state ->
            state.outboundSessionIds.remove(sessionId)
            logger.info("Unregistered outbound session $sessionId for device $deviceId (remaining: ${state.outboundSessionIds.size})")

            if (state.inboundSessionId == null && state.outboundSessionIds.isEmpty()) {
                logger.info("No connections remaining, stopping pipeline for device $deviceId")
                stopPipeline(deviceId)
            }
        }
    }

    fun feedVideoFrame(deviceId: UUID, jpegData: ByteArray, pts: Long) {
        pipelines[deviceId]?.pipeline?.feedVideoFrame(jpegData, pts)
            ?: logger.warn("No pipeline found for device $deviceId to feed video frame")
    }

    fun feedAudioFrame(deviceId: UUID, aacData: ByteArray, pts: Long) {
        pipelines[deviceId]?.pipeline?.feedAudioFrame(aacData, pts)
            ?: logger.warn("No pipeline found for device $deviceId to feed audio frame")
    }

    fun subscribeToClusterFlow(deviceId: UUID): SharedFlow<ByteArray>? {
        return pipelines[deviceId]?.pipeline?.subscribeToClusterFlow()
    }

    fun getInitSegment(deviceId: UUID): ByteArray? {
        return pipelines[deviceId]?.pipeline?.getInitSegment()
    }

    fun hasPipeline(deviceId: UUID): Boolean {
        return pipelines.containsKey(deviceId)
    }

    fun getActiveDeviceIds(): Set<UUID> {
        return pipelines.keys.toSet()
    }

    suspend fun stopAll() {
        logger.info("Stopping all pipelines")
        val deviceIds = pipelines.keys.toList()
        deviceIds.forEach { deviceId ->
            try {
                stopPipeline(deviceId)
            } catch (e: Exception) {
                logger.error("Error stopping pipeline for device $deviceId", e)
            }
        }
    }
}
