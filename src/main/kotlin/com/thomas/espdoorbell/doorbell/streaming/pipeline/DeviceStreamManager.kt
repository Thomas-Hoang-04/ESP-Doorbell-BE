package com.thomas.espdoorbell.doorbell.streaming.pipeline

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.SegmentData
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton service that manages all device transcoding pipelines
 * Coordinates inbound ESP32 connections and outbound Android client subscriptions
 */
// TODO: Persist active streams to database (Events table)
// TODO: Implement stream timeout (auto-stop after N minutes of inactivity)
// TODO: Add device quota limits (max concurrent streams per device)
// TODO: Emit events for stream start/stop to EventService
@Service
class DeviceStreamManager(
    private val config: StreamingProperties,
    private val deviceService: DeviceService
) {
    private val logger = LoggerFactory.getLogger(DeviceStreamManager::class.java)

    private val pipelines = ConcurrentHashMap<UUID, PipelineState>()

    data class PipelineState(
        val pipeline: DeviceTranscodingPipeline,
        var inboundSessionId: String? = null,
        val outboundSessionIds: MutableSet<String> = mutableSetOf()
    )

    /**
     * Start a pipeline for a device if not already running
     */
    suspend fun startPipeline(deviceId: UUID): DeviceTranscodingPipeline {
        return pipelines.computeIfAbsent(deviceId) {
            logger.info("Creating new pipeline for device $deviceId")
            val pipeline = DeviceTranscodingPipeline(
                deviceId = deviceId,
                config = config,
                deviceService = deviceService
            )
            PipelineState(pipeline = pipeline)
        }.pipeline.also { pipeline ->
            // Start pipeline if not already started
            try {
                pipeline.start()
            } catch (e: Exception) {
                logger.error("Failed to start pipeline for device $deviceId", e)
                pipelines.remove(deviceId)
                throw e
            }
        }
    }

    /**
     * Stop a pipeline for a device
     */
    suspend fun stopPipeline(deviceId: UUID) {
        pipelines.remove(deviceId)?.let { state ->
            logger.info("Stopping pipeline for device $deviceId")
            try {
                state.pipeline.stop()
            } catch (e: Exception) {
                logger.error("Error stopping pipeline for device $deviceId", e)
            }
        }
    }

    /**
     * Register inbound ESP32 connection
     * Enforces single ESP32 per device
     */
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

    /**
     * Unregister inbound ESP32 connection
     * Stops pipeline if no outbound clients are connected
     */
    suspend fun unregisterInbound(deviceId: UUID, sessionId: String) {
        pipelines[deviceId]?.let { state ->
            if (state.inboundSessionId == sessionId) {
                state.inboundSessionId = null
                logger.info("Unregistered inbound session $sessionId for device $deviceId")

                // Stop pipeline if no outbound clients
                if (state.outboundSessionIds.isEmpty()) {
                    logger.info("No outbound clients, stopping pipeline for device $deviceId")
                    stopPipeline(deviceId)
                }
            }
        }
    }

    /**
     * Register outbound Android client connection
     */
    fun registerOutbound(deviceId: UUID, sessionId: String) {
        pipelines[deviceId]?.let { state ->
            state.outboundSessionIds.add(sessionId)
            logger.info("Registered outbound session $sessionId for device $deviceId (total: ${state.outboundSessionIds.size})")
        } ?: run {
            logger.warn("Attempted to register outbound for device $deviceId but no pipeline exists")
        }
    }

    /**
     * Unregister outbound Android client connection
     * Stops pipeline if no inbound connection and no other outbound clients
     */
    suspend fun unregisterOutbound(deviceId: UUID, sessionId: String) {
        pipelines[deviceId]?.let { state ->
            state.outboundSessionIds.remove(sessionId)
            logger.info("Unregistered outbound session $sessionId for device $deviceId (remaining: ${state.outboundSessionIds.size})")

            // Stop pipeline if no inbound and no outbound clients
            if (state.inboundSessionId == null && state.outboundSessionIds.isEmpty()) {
                logger.info("No connections remaining, stopping pipeline for device $deviceId")
                stopPipeline(deviceId)
            }
        }
    }

    /**
     * Feed video frame to device pipeline
     */
    fun feedVideoFrame(deviceId: UUID, jpegData: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        pipelines[deviceId]?.pipeline?.feedVideoFrame(jpegData, pts, dts, sequenceNumber)
            ?: logger.warn("No pipeline found for device $deviceId to feed video frame")
    }

    /**
     * Feed audio frame to device pipeline
     */
    fun feedAudioFrame(deviceId: UUID, aacData: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        pipelines[deviceId]?.pipeline?.feedAudioFrame(aacData, pts, dts, sequenceNumber)
            ?: logger.warn("No pipeline found for device $deviceId to feed audio frame")
    }

    /**
     * Subscribe to segment flow for a device
     */
    fun subscribeToSegments(deviceId: UUID): Flow<SegmentData>? {
        return pipelines[deviceId]?.pipeline?.subscribeToSegments()
    }

    /**
     * Get buffered segments for late joiners
     */
    fun getBufferedSegments(deviceId: UUID): List<SegmentData> {
        return pipelines[deviceId]?.pipeline?.getBufferedSegments() ?: emptyList()
    }

    /**
     * Get the WebM initialization segment for late-joining clients.
     * This must be sent before any clusters for the client to decode the stream.
     */
    fun getInitSegment(deviceId: UUID): ByteArray? {
        return pipelines[deviceId]?.pipeline?.getInitSegment()
    }

    /**
     * Check if device has an active pipeline
     */
    fun hasPipeline(deviceId: UUID): Boolean {
        return pipelines.containsKey(deviceId)
    }

    /**
     * Get all active device IDs
     */
    fun getActiveDeviceIds(): Set<UUID> {
        return pipelines.keys.toSet()
    }

    /**
     * Stop all pipelines (for shutdown)
     */
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

