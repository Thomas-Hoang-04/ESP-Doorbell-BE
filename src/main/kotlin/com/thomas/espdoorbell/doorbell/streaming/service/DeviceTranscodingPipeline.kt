package com.thomas.espdoorbell.doorbell.streaming.service

import com.thomas.espdoorbell.doorbell.model.entity.events.StreamEvents
import com.thomas.espdoorbell.doorbell.model.types.StreamStatus
import com.thomas.espdoorbell.doorbell.repository.event.EventStreamRepository
import com.thomas.espdoorbell.doorbell.service.device.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import com.thomas.espdoorbell.doorbell.streaming.model.SegmentData
import com.thomas.espdoorbell.doorbell.streaming.service.transcoding.FFmpegProcessManager
import com.thomas.espdoorbell.doorbell.streaming.service.transcoding.FrameReorderingBuffer
import com.thomas.espdoorbell.doorbell.streaming.service.transcoding.SegmentBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.OffsetDateTime
import java.util.*

/**
 * Per-device transcoding pipeline orchestrator
 * Manages FFmpeg process, frame reordering, and segment broadcasting
 * for a single ESP32 device
 */
class DeviceTranscodingPipeline(
    private val deviceId: UUID,
    private val config: StreamingProperties,
    private val streamEventRepository: EventStreamRepository,
    private val deviceService: DeviceService
) {
    private val logger = LoggerFactory.getLogger(DeviceTranscodingPipeline::class.java)

    // Working directory
    private lateinit var workingDirectory: Path

    // Component instances
    private lateinit var ffmpegManager: FFmpegProcessManager
    private lateinit var videoBuffer: FrameReorderingBuffer
    private lateinit var audioBuffer: FrameReorderingBuffer
    private lateinit var segmentBroadcaster: SegmentBroadcaster

    // Lifecycle
    @Volatile
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamEventId: UUID? = null

    /**
     * Start the pipeline: create directories, initialize components, start processing
     */
    suspend fun start() {
        logger.info("Starting transcoding pipeline for device $deviceId")
        
        try {
            // Validate device exists
            deviceService.getDevice(deviceId)

            // Create working directory
            workingDirectory = Paths.get(config.workingDirectory, deviceId.toString())
            Files.createDirectories(workingDirectory)
            logger.info("Created working directory: $workingDirectory")

            // Create StreamEvents record
            val streamEvent = StreamEvents(
                event = deviceId,
                streamStatus = StreamStatus.STREAMING,
                startedAt = OffsetDateTime.now()
            )
            streamEventRepository.save(streamEvent)
            streamEventId = deviceId
            logger.info("Created StreamEvents record")

            // Initialize components
            initializeComponents()

            // Start components
            ffmpegManager.start(scope)
            videoBuffer.startProcessing(scope)
            audioBuffer.startProcessing(scope)
            segmentBroadcaster.start(scope)

            // Mark as running
            running = true

            logger.info("Pipeline started successfully for device $deviceId")
        } catch (e: Exception) {
            logger.error("Failed to start pipeline for device $deviceId", e)
            cleanup()
            throw e
        }
    }

    /**
     * Feed video frame into the pipeline
     */
    fun feedVideoFrame(jpegData: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        if (!running) return
        videoBuffer.offer(jpegData, pts, dts, sequenceNumber)
    }

    /**
     * Feed audio frame into the pipeline
     */
    fun feedAudioFrame(aacData: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        if (!running) return
        audioBuffer.offer(aacData, pts, dts, sequenceNumber)
    }

    /**
     * Subscribe to segment flow
     */
    fun subscribeToSegments(): SharedFlow<SegmentData> = segmentBroadcaster.subscribeToSegments()

    /**
     * Get buffered segments for late joiners
     */
    fun getBufferedSegments(): List<SegmentData> = segmentBroadcaster.getBufferedSegments()

    /**
     * Stop the pipeline and cleanup resources
     */
    suspend fun stop() {
        logger.info("Stopping transcoding pipeline for device $deviceId")
        running = false

        // Stop components
        videoBuffer.stop()
        audioBuffer.stop()
        segmentBroadcaster.stop()

        // Flush buffers
        videoBuffer.flush()
        audioBuffer.flush()

        // Stop FFmpeg
        ffmpegManager.stop()

        // Cancel coroutines
        scope.cancel()

        // Update StreamEvents
        streamEventId?.let { id ->
            try {
                val streamEvent = streamEventRepository.findById(id)
                streamEvent?.let {
                    streamEventRepository.save(
                        StreamEvents(
                            event = id,
                            streamStatus = StreamStatus.COMPLETED,
                            startedAt = it.toDto().startedAt,
                            endedAt = OffsetDateTime.now()
                        )
                    )
                }
            } catch (e: Exception) {
                logger.error("Failed to update StreamEvents", e)
            }
        }

        // Cleanup files
        cleanup()

        logger.info("Pipeline stopped for device $deviceId")
    }

    private fun initializeComponents() {
        // Create FFmpeg process manager
        ffmpegManager = FFmpegProcessManager(workingDirectory, config)

        // Create frame reordering buffers with write callbacks
        videoBuffer = FrameReorderingBuffer(
            config = config.buffer,
            streamType = "video",
            writeFrame = { frame -> writeVideoFrame(frame) }
        )

        audioBuffer = FrameReorderingBuffer(
            config = config.buffer,
            streamType = "audio",
            writeFrame = { frame -> writeAudioFrame(frame) }
        )

        // Create segment broadcaster
        segmentBroadcaster = SegmentBroadcaster(workingDirectory, config.segment)

        logger.info("Initialized all pipeline components")
    }

    @Synchronized
    private fun writeVideoFrame(frame: FrameReorderingBuffer.TimedFrame) {
        ffmpegManager.getVideoOutputStream()?.let { stream ->
            try {
                stream.write(frame.data)
                stream.flush()
            } catch (e: IOException) {
                logger.error("Error writing video frame", e)
            }
        }
    }

    @Synchronized
    private fun writeAudioFrame(frame: FrameReorderingBuffer.TimedFrame) {
        ffmpegManager.getAudioOutputStream()?.let { stream ->
            try {
                stream.write(frame.data)
                stream.flush()
            } catch (e: IOException) {
                logger.error("Error writing audio frame", e)
            }
        }
    }

    private fun cleanup() {
        try {
            // Delete all segment files
            if (::workingDirectory.isInitialized && Files.exists(workingDirectory)) {
                Files.list(workingDirectory).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".webm") }
                        .forEach { Files.deleteIfExists(it) }
                }
                
                Files.deleteIfExists(workingDirectory)
            }
        } catch (e: Exception) {
            logger.error("Error during cleanup", e)
        }
    }
}
