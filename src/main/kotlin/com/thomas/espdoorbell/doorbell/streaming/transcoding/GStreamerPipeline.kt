package com.thomas.espdoorbell.doorbell.streaming.transcoding

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.freedesktop.gstreamer.*
import org.freedesktop.gstreamer.elements.AppSink
import org.freedesktop.gstreamer.elements.AppSrc
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Native GStreamer pipeline for transcoding MJPEG+AAC to WebM (VP8+Opus).
 * Uses appsrc for frame injection and appsink for WebM cluster output.
 */
class GStreamerPipeline(
    private val config: StreamingProperties,
    private val scope: CoroutineScope
) {
    private val logger = LoggerFactory.getLogger(GStreamerPipeline::class.java)

    companion object {
        private val GST_INITIALIZED = AtomicBoolean(false)
        private val CLUSTER_ID = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75)
        
        @Synchronized
        fun ensureGstInitialized() {
            if (GST_INITIALIZED.compareAndSet(false, true)) {
                Gst.init(Version.BASELINE, "doorbell-transcoder")
            }
        }
    }

    private var pipeline: Pipeline? = null
    private var videoAppSrc: AppSrc? = null
    private var audioAppSrc: AppSrc? = null
    private var appSink: AppSink? = null

    private val _clusterFlow = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 10)
    val clusterFlow: SharedFlow<ByteArray> = _clusterFlow.asSharedFlow()

    @Volatile
    var initSegment: ByteArray? = null
        private set

    private val accumulator = java.io.ByteArrayOutputStream(256 * 1024)
    private var clusterCount = 0

    @Volatile
    private var running = false

    private val videoFrameCount = AtomicLong(0)
    private val audioFrameCount = AtomicLong(0)
    private var videoBasePts: Long? = null
    private var audioBasePts: Long? = null

    fun start() {
        logger.info("Starting GStreamer pipeline")
        ensureGstInitialized()

        running = true

        val gstConfig = config.gstreamer
        
        // Build pipeline description
        val pipelineDesc = buildPipelineDescription(gstConfig)
        logger.info("GStreamer pipeline: {}", pipelineDesc)

        try {
            pipeline = Gst.parseLaunch(pipelineDesc) as Pipeline

            // Get references to appsrc and appsink elements
            videoAppSrc = pipeline?.getElementByName("video_src") as? AppSrc
            audioAppSrc = pipeline?.getElementByName("audio_src") as? AppSrc
            appSink = pipeline?.getElementByName("sink") as? AppSink

            if (videoAppSrc == null || audioAppSrc == null || appSink == null) {
                throw IllegalStateException("Failed to get pipeline elements")
            }

            // Configure video appsrc
            videoAppSrc?.apply {
                caps = Caps.fromString("image/jpeg,framerate=0/1")
                set("format", Format.TIME)
                set("is-live", true)
                set("block", false)
                set("max-bytes", 0L)
            }

            audioAppSrc?.apply {
                caps = Caps.fromString("audio/mpeg,mpegversion=4,stream-format=adts,channels=2,rate=48000")
                set("format", Format.TIME)
                set("is-live", true)
                set("block", false)
                set("max-bytes", 0L)
            }

            // Configure appsink to receive WebM output
            appSink?.apply {
                set("emit-signals", true)
                set("sync", false)
                set("max-buffers", 10)
                set("drop", true)

                connect(AppSink.NEW_SAMPLE { sink ->
                    handleNewSample(sink)
                    FlowReturn.OK
                })
            }

            // Set up bus message handling
            pipeline?.bus?.connect(Bus.ERROR { _, _, message ->
                logger.error("GStreamer error: {}", message)
            })

            pipeline?.bus?.connect(Bus.WARNING { _, _, message ->
                logger.warn("GStreamer warning: {}", message)
            })

            pipeline?.bus?.connect(Bus.EOS {
                logger.info("GStreamer EOS received")
                running = false
            })

            // Start the pipeline
            val result = pipeline?.play()
            if (result != StateChangeReturn.SUCCESS && result != StateChangeReturn.ASYNC) {
                throw IllegalStateException("Failed to start pipeline: $result")
            }

            logger.info("GStreamer pipeline started successfully")
        } catch (e: Exception) {
            logger.error("Failed to start GStreamer pipeline", e)
            stop()
            throw e
        }
    }

    private fun buildPipelineDescription(gstConfig: StreamingProperties.GStreamerSettings): String {
        val videoBitrate = parseBitrate(gstConfig.videoBitrate)
        val audioBitrate = parseBitrate(gstConfig.audioBitrate)

        return """
            appsrc name=video_src ! 
            jpegdec ! 
            videoconvert ! 
            vp8enc deadline=1 cpu-used=${gstConfig.cpuUsed} target-bitrate=$videoBitrate keyframe-max-dist=30 threads=4 error-resilient=default ! 
            queue max-size-time=0 max-size-bytes=0 max-size-buffers=3 ! 
            mux.
            
            appsrc name=audio_src ! 
            aacparse ! 
            avdec_aac ! 
            audioconvert ! 
            audioresample ! 
            audio/x-raw,rate=48000,channels=2 ! 
            opusenc bitrate=$audioBitrate frame-size=${gstConfig.opusFrameSizeMs} ! 
            queue max-size-time=0 max-size-bytes=0 max-size-buffers=3 ! 
            mux.
            
            webmmux name=mux streamable=true ! 
            appsink name=sink
        """.trimIndent().replace("\n", " ")
    }

    private fun parseBitrate(bitrate: String): Int {
        val cleaned = bitrate.trim().lowercase()
        return when {
            cleaned.endsWith("m") -> (cleaned.dropLast(1).toDouble() * 1_000_000).toInt()
            cleaned.endsWith("k") -> (cleaned.dropLast(1).toDouble() * 1_000).toInt()
            else -> cleaned.toIntOrNull() ?: 1_000_000
        }
    }

    fun feedVideoFrame(jpegData: ByteArray, pts: Long) {
        if (!running) return
        val src = videoAppSrc ?: return

        try {
            // Normalize PTS
            if (videoBasePts == null) {
                videoBasePts = pts
            }
            val normalizedPts = (pts - (videoBasePts ?: 0)) * 1_000_000 // Convert ms to ns

            val buffer = Buffer(jpegData.size)
            buffer.map(true).put(ByteBuffer.wrap(jpegData))
            buffer.unmap()
            buffer.presentationTimestamp = normalizedPts

            val result = src.pushBuffer(buffer)
            if (result != FlowReturn.OK) {
                logger.warn("Video buffer push returned: {}", result)
            } else {
                videoFrameCount.incrementAndGet()
            }
        } catch (e: Exception) {
            logger.error("Error feeding video frame", e)
        }
    }

    fun feedAudioFrame(aacData: ByteArray, pts: Long) {
        if (!running) return
        val src = audioAppSrc ?: return

        try {
            // Normalize PTS
            if (audioBasePts == null) {
                audioBasePts = pts
            }
            val normalizedPts = (pts - (audioBasePts ?: 0)) * 1_000_000 // Convert ms to ns

            val buffer = Buffer(aacData.size)
            buffer.map(true).put(ByteBuffer.wrap(aacData))
            buffer.unmap()
            buffer.presentationTimestamp = normalizedPts

            val result = src.pushBuffer(buffer)
            if (result != FlowReturn.OK) {
                logger.warn("Audio buffer push returned: {}", result)
            } else {
                audioFrameCount.incrementAndGet()
            }
        } catch (e: Exception) {
            logger.error("Error feeding audio frame", e)
        }
    }

    private fun handleNewSample(sink: AppSink) {
        try {
            val sample = sink.pullSample() ?: return
            val buffer = sample.buffer ?: return

            val byteBuffer = buffer.map(false)
            if (byteBuffer != null) {
                val size = byteBuffer.remaining()
                val data = ByteArray(size)
                byteBuffer.get(data)
                buffer.unmap()

                scope.launch(Dispatchers.IO) {
                    processWebMData(data)
                }
            }

            sample.dispose()
        } catch (e: Exception) {
            logger.error("Error handling new sample", e)
        }
    }

    private suspend fun processWebMData(data: ByteArray) =
        withContext(Dispatchers.IO) {
            accumulator.write(data)
            val bytes = accumulator.toByteArray()

            var lastClusterEnd = 0
            var pos = 0

            while (pos <= bytes.size - 4) {
                if (isClusterStart(bytes, pos)) {
                    if (initSegment == null && pos > 0) {
                        initSegment = bytes.copyOfRange(0, pos)
                        logger.info("Init segment captured: {} bytes", initSegment!!.size)
                        lastClusterEnd = pos
                    } else if (pos > lastClusterEnd && initSegment != null) {
                        emitCluster(bytes.copyOfRange(lastClusterEnd, pos))
                        lastClusterEnd = pos
                    }
                }
                pos++
            }

            accumulator.reset()
            if (lastClusterEnd < bytes.size) {
                accumulator.write(bytes, lastClusterEnd, bytes.size - lastClusterEnd)
            }
        }

    private fun isClusterStart(data: ByteArray, pos: Int): Boolean {
        if (pos + 4 > data.size) return false
        return data[pos] == CLUSTER_ID[0] &&
               data[pos + 1] == CLUSTER_ID[1] &&
               data[pos + 2] == CLUSTER_ID[2] &&
               data[pos + 3] == CLUSTER_ID[3]
    }

    private suspend fun emitCluster(data: ByteArray) {
        clusterCount++
        logger.debug("Emitting cluster {}: {} bytes", clusterCount, data.size)
        _clusterFlow.emit(data)
    }

    fun stop() {
        logger.info("Stopping GStreamer pipeline. Stats: video={}, audio={}, clusters={}",
            videoFrameCount.get(), audioFrameCount.get(), clusterCount)

        running = false

        try {
            // Send EOS to properly flush the pipeline
            videoAppSrc?.endOfStream()
            audioAppSrc?.endOfStream()

            // Stop and clean up pipeline
            pipeline?.stop()
            pipeline?.dispose()
        } catch (e: Exception) {
            logger.error("Error stopping GStreamer pipeline", e)
        }

        pipeline = null
        videoAppSrc = null
        audioAppSrc = null
        appSink = null

        logger.info("GStreamer pipeline stopped")
    }
}
