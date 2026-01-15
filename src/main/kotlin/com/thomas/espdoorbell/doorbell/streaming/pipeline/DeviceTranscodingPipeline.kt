package com.thomas.espdoorbell.doorbell.streaming.pipeline

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import com.thomas.espdoorbell.doorbell.streaming.transcoding.GStreamerPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import org.slf4j.LoggerFactory
import java.util.UUID

class DeviceTranscodingPipeline(
    private val deviceId: UUID,
    private val config: StreamingProperties,
    private val healthTracker: PipelineHealthTracker
) {
    private val logger = LoggerFactory.getLogger(DeviceTranscodingPipeline::class.java)

    private lateinit var gstreamerPipeline: GStreamerPipeline

    @Volatile
    private var running = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        logger.info("Starting GStreamer transcoding pipeline for device {}", deviceId)

        try {
            gstreamerPipeline = GStreamerPipeline(config, scope)
            gstreamerPipeline.start()

            running = true
            healthTracker.registerPipeline(deviceId)
            logger.info("GStreamer pipeline started successfully for device {}", deviceId)
        } catch (e: Exception) {
            logger.error("Failed to start GStreamer pipeline for device {}", deviceId, e)
            stop()
            throw e
        }
    }

    fun feedVideoFrame(jpegData: ByteArray, pts: Long) {
        if (!running || !::gstreamerPipeline.isInitialized) return
        gstreamerPipeline.feedVideoFrame(jpegData, pts)
        healthTracker.recordFrame(deviceId)
    }

    fun feedAudioFrame(aacData: ByteArray, pts: Long) {
        if (!running || !::gstreamerPipeline.isInitialized) return
        gstreamerPipeline.feedAudioFrame(aacData, pts)
    }

    fun subscribeToClusterFlow(): SharedFlow<ByteArray> {
        return gstreamerPipeline.clusterFlow
    }

    fun getInitSegment(): ByteArray? {
        return if (::gstreamerPipeline.isInitialized) gstreamerPipeline.initSegment else null
    }

    fun stop() {
        logger.info("Stopping GStreamer transcoding pipeline for device {}", deviceId)
        running = false

        healthTracker.unregisterPipeline(deviceId)

        if (::gstreamerPipeline.isInitialized) {
            gstreamerPipeline.stop()
        }

        scope.cancel()

        logger.info("GStreamer pipeline stopped for device {}", deviceId)
    }
}
