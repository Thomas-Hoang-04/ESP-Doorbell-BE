package com.thomas.espdoorbell.doorbell.streaming.pipeline

import com.thomas.espdoorbell.doorbell.device.service.DeviceService
import com.thomas.espdoorbell.doorbell.streaming.buffer.BackpressureChannel
import com.thomas.espdoorbell.doorbell.streaming.buffer.SegmentRingBuffer
import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.SegmentData
import com.thomas.espdoorbell.doorbell.streaming.transcoding.FFmpegProcessManager
import com.thomas.espdoorbell.doorbell.streaming.transcoding.UnifiedFrameWriter
import com.thomas.espdoorbell.doorbell.streaming.transcoding.WebMStreamParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Per-device transcoding pipeline orchestrator.
 * 
 * Pipeline flow:
 * ESP32 → UnifiedFrameWriter → FFmpeg → WebMStreamParser → Clients
 *         (reorder + sync)    (pipes)   (stdout parse)
 * 
 * Uses in-memory streaming: parses WebM clusters from FFmpeg stdout for low latency.
 */
// TODO: Integrate with EventService to track stream lifecycle
// TODO: Add FFmpeg process health monitoring
// TODO: Implement auto-restart on FFmpeg crash
class DeviceTranscodingPipeline(
    private val deviceId: UUID,
    private val config: StreamingProperties,
    private val deviceService: DeviceService
) {
    private val logger = LoggerFactory.getLogger(DeviceTranscodingPipeline::class.java)

    // Working directory for FFmpeg pipes
    private lateinit var workingDirectory: Path

    // Core components
    private lateinit var ffmpegManager: FFmpegProcessManager
    private lateinit var frameWriter: UnifiedFrameWriter
    private lateinit var streamParser: WebMStreamParser
    private lateinit var segmentRingBuffer: SegmentRingBuffer
    private lateinit var backpressureChannel: BackpressureChannel

    // Lifecycle
    @Volatile
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start the pipeline: create directories, initialize components, start processing
     */
    suspend fun start() {
        logger.info("Starting transcoding pipeline for device {}", deviceId)
        
        try {
            // Validate device exists
            deviceService.getDevice(deviceId)

            // Create working directory for FFmpeg pipes
            workingDirectory = Paths.get(config.workingDirectory, deviceId.toString())
            Files.createDirectories(workingDirectory)
            logger.info("Created working directory: {}", workingDirectory)

            // Initialize all components
            initializeComponents()

            // Start FFmpeg first (creates pipes)
            ffmpegManager.start(scope)
            
            // Wait for pipes to be ready, then start frame writer
            scope.launch {
                delay(100) // Give FFmpeg time to create pipes
                
                val videoStream = ffmpegManager.getVideoOutputStream()
                val audioStream = ffmpegManager.getAudioOutputStream()
                
                if (videoStream != null && audioStream != null) {
                    frameWriter = UnifiedFrameWriter(videoStream, audioStream, config.buffer)
                    frameWriter.start(scope)
                    logger.info("UnifiedFrameWriter started")
                } else {
                    logger.error("Failed to get FFmpeg streams")
                }
            }

            // Start WebM stream parser on FFmpeg stdout
            ffmpegManager.getStdoutStream()?.let { stdout ->
                streamParser.startParsing(stdout, scope)
                logger.info("WebM stream parser started on FFmpeg stdout")
            } ?: throw IllegalStateException("Failed to get FFmpeg stdout stream")

            running = true
            logger.info("Pipeline started successfully for device {}", deviceId)
        } catch (e: Exception) {
            logger.error("Failed to start pipeline for device {}", deviceId, e)
            cleanup()
            throw e
        }
    }

    /**
     * Feed video frame into the pipeline
     */
    fun feedVideoFrame(jpegData: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        if (!running || !::frameWriter.isInitialized) return
        frameWriter.offerVideo(jpegData, pts, dts, sequenceNumber)
    }

    /**
     * Feed audio frame into the pipeline
     */
    fun feedAudioFrame(aacData: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        if (!running || !::frameWriter.isInitialized) return
        frameWriter.offerAudio(aacData, pts, dts, sequenceNumber)
    }

    /**
     * Subscribe to segment flow
     */
    fun subscribeToSegments(): Flow<SegmentData> = backpressureChannel.asFlow()

    /**
     * Get buffered segments for late joiners
     */
    fun getBufferedSegments(): List<SegmentData> = segmentRingBuffer.getBufferedSegments()

    /**
     * Get the initialization segment for late-joining clients
     */
    fun getInitSegment(): ByteArray? = streamParser.initSegment

    /**
     * Stop the pipeline and cleanup resources
     */
    fun stop() {
        logger.info("Stopping transcoding pipeline for device {}", deviceId)
        running = false

        // Stop components
        if (::frameWriter.isInitialized) {
            frameWriter.stop()
            frameWriter.flush()
        }
        streamParser.stop()
        backpressureChannel.close()

        // Stop FFmpeg
        ffmpegManager.stop()

        // Cancel coroutines
        scope.cancel()

        logger.info("Pipeline stopped for device {}", deviceId)
    }

    private fun initializeComponents() {
        // Initialize segment distribution
        segmentRingBuffer = SegmentRingBuffer(capacity = config.segment.bufferCount)
        backpressureChannel = BackpressureChannel(capacity = 10)

        // Initialize stream parser
        streamParser = WebMStreamParser(segmentRingBuffer, backpressureChannel)

        // Initialize FFmpeg (outputs WebM to stdout)
        ffmpegManager = FFmpegProcessManager(workingDirectory, config)

        // UnifiedFrameWriter is initialized after FFmpeg starts (needs pipe streams)
        logger.info("Initialized pipeline components for device {}", deviceId)
    }

    private fun cleanup() {
        try {
            if (::workingDirectory.isInitialized && Files.exists(workingDirectory)) {
                Files.deleteIfExists(workingDirectory.resolve("video.pipe"))
                Files.deleteIfExists(workingDirectory.resolve("audio.pipe"))
                Files.deleteIfExists(workingDirectory)
            }
        } catch (e: Exception) {
            logger.error("Error during cleanup", e)
        }
    }
}
