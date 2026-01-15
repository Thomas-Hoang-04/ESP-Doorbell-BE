package com.thomas.espdoorbell.doorbell.streaming.pipeline

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import com.thomas.espdoorbell.doorbell.streaming.transcoding.FFmpegProcessManager
import com.thomas.espdoorbell.doorbell.streaming.transcoding.WebMStreamRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID

class DeviceTranscodingPipeline(
    private val deviceId: UUID,
    private val config: StreamingProperties,
    private val healthTracker: PipelineHealthTracker
) {
    private val logger = LoggerFactory.getLogger(DeviceTranscodingPipeline::class.java)

    private lateinit var frameForwarder: SyncTcpFrameForwarder
    private lateinit var ffmpegManager: FFmpegProcessManager
    private lateinit var streamRelay: WebMStreamRelay

    @Volatile
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun start() {
        logger.info("Starting transcoding pipeline for device {}", deviceId)

        try {
            frameForwarder = SyncTcpFrameForwarder(config)
            frameForwarder.start(scope)

            delay(50)

            val videoPort = frameForwarder.videoPort
            val audioPort = frameForwarder.audioPort

            if (videoPort == 0 || audioPort == 0) {
                throw IllegalStateException("Failed to get TCP ports from forwarder")
            }

            ffmpegManager = FFmpegProcessManager(config, videoPort, audioPort)
            ffmpegManager.start(scope)

            streamRelay = WebMStreamRelay()

            scope.launch {
                delay(500)

                val stdout = ffmpegManager.getStdoutStream()
                if (stdout != null) {
                    streamRelay.startRelaying(stdout, scope)
                    logger.info("WebM stream relay started")
                } else {
                    logger.error("Failed to get FFmpeg stdout stream")
                }
            }

            running = true
            healthTracker.registerPipeline(deviceId)
            logger.info("Pipeline started successfully for device {}", deviceId)
        } catch (e: Exception) {
            logger.error("Failed to start pipeline for device {}", deviceId, e)
            stop()
            throw e
        }
    }

    fun feedVideoFrame(jpegData: ByteArray, pts: Long) {
        if (!running || !::frameForwarder.isInitialized) return
        frameForwarder.offerVideo(jpegData, pts)
        healthTracker.recordFrame(deviceId)
    }

    fun feedAudioFrame(aacData: ByteArray, pts: Long) {
        if (!running || !::frameForwarder.isInitialized) return
        frameForwarder.offerAudio(aacData, pts)
    }

    fun subscribeToClusterFlow(): SharedFlow<ByteArray> {
        return streamRelay.clusterFlow
    }

    fun getInitSegment(): ByteArray? {
        return if (::streamRelay.isInitialized) streamRelay.initSegment else null
    }

    fun stop() {
        logger.info("Stopping transcoding pipeline for device {}", deviceId)
        running = false

        healthTracker.unregisterPipeline(deviceId)

        if (::streamRelay.isInitialized) {
            streamRelay.stop()
        }

        if (::ffmpegManager.isInitialized) {
            ffmpegManager.stop()
        }

        if (::frameForwarder.isInitialized) {
            frameForwarder.stop()
        }

        scope.cancel()

        logger.info("Pipeline stopped for device {}", deviceId)
    }
}
