package com.thomas.espdoorbell.doorbell.streaming.transcoding

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * Unified frame processor that handles both network reordering and A/V synchronization.
 * 
 * Pipeline:
 * 1. REORDER STAGE: Frames are buffered by sequence number to handle out-of-order packets
 * 2. SYNC STAGE: Reordered frames are interleaved by PTS for proper A/V lip sync
 * 3. WRITE STAGE: Synchronized frames are written to FFmpeg pipes
 * 
 * Features:
 * - Sequence-based reordering to handle network packet loss/reorder
 * - PTS-based A/V interleaving for proper playback sync
 * - Stale frame flushing to prevent pipeline stalls
 * - Configurable timeouts and buffer sizes
 */
class UnifiedFrameWriter(
    private val videoStream: OutputStream,
    private val audioStream: OutputStream,
    private val config: StreamingProperties.BufferSettings
) {
    private val logger = LoggerFactory.getLogger(UnifiedFrameWriter::class.java)
    
    // Frame data structure
    data class Frame(
        val data: ByteArray,
        val pts: Long,              // Presentation timestamp (microseconds)
        val dts: Long,              // Decode timestamp (microseconds)
        val sequenceNumber: Int,    // Sequence from ESP32
        val arrivalTime: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return pts == other.pts && sequenceNumber == other.sequenceNumber
        }
        override fun hashCode(): Int = 31 * pts.hashCode() + sequenceNumber
    }
    
    // Stage 1: Reordering buffers (by sequence number)
    private val videoReorderQueue = PriorityBlockingQueue<Frame>(100) { a, b ->
        a.sequenceNumber.compareTo(b.sequenceNumber)
    }
    private val audioReorderQueue = PriorityBlockingQueue<Frame>(100) { a, b ->
        a.sequenceNumber.compareTo(b.sequenceNumber)
    }
    
    // Stage 2: Sync buffers (by PTS)
    private val videoSyncQueue = PriorityBlockingQueue<Frame>(50) { a, b ->
        a.pts.compareTo(b.pts)
    }
    private val audioSyncQueue = PriorityBlockingQueue<Frame>(50) { a, b ->
        a.pts.compareTo(b.pts)
    }
    
    // Sequence tracking
    private var nextVideoSeq = -1
    private var nextAudioSeq = -1
    
    // PTS normalization bases
    @Volatile private var videoBasePts: Long? = null
    @Volatile private var audioBasePts: Long? = null
    
    // Statistics
    private val videoFramesWritten = AtomicLong(0)
    private val audioFramesWritten = AtomicLong(0)
    private val reorderedFrames = AtomicLong(0)
    
    @Volatile private var running = false
    
    /**
     * Start the background processing loop.
     */
    fun start(scope: CoroutineScope) {
        running = true
        logger.info("UnifiedFrameWriter started")
        
        // Background processing loop
        scope.launch(Dispatchers.IO) {
            while (running && isActive) {
                try {
                    processReorderQueues()
                    processSyncQueues()
                    flushStaleFrames()
                } catch (e: Exception) {
                    logger.error("Error in frame processing loop", e)
                }
                delay(5) // Process every 5ms
            }
        }
    }
    
    /**
     * Stop processing.
     */
    fun stop() {
        running = false
        logger.info("UnifiedFrameWriter stopped. Stats: video={}, audio={}, reordered={}",
            videoFramesWritten.get(), audioFramesWritten.get(), reorderedFrames.get())
    }
    
    /**
     * Queue a video frame for processing.
     */
    fun offerVideo(data: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        if (!running) return
        
        // Initialize sequence on first frame
        if (nextVideoSeq < 0) {
            nextVideoSeq = sequenceNumber
            logger.info("Video stream initialized at seq={}", sequenceNumber)
        }
        
        videoReorderQueue.offer(Frame(data, pts, dts, sequenceNumber))
    }
    
    /**
     * Queue an audio frame for processing.
     */
    fun offerAudio(data: ByteArray, pts: Long, dts: Long, sequenceNumber: Int) {
        if (!running) return
        
        // Initialize sequence on first frame
        if (nextAudioSeq < 0) {
            nextAudioSeq = sequenceNumber
            logger.info("Audio stream initialized at seq={}", sequenceNumber)
        }
        
        audioReorderQueue.offer(Frame(data, pts, dts, sequenceNumber))
    }
    
    /**
     * Flush all remaining frames on shutdown.
     */
    fun flush() {
        logger.info("Flushing remaining frames: video reorder={}, audio reorder={}, video sync={}, audio sync={}",
            videoReorderQueue.size, audioReorderQueue.size, videoSyncQueue.size, audioSyncQueue.size)
        
        // Move all from reorder to sync
        drainQueueTo(videoReorderQueue, videoSyncQueue)
        drainQueueTo(audioReorderQueue, audioSyncQueue)
        
        // Flush sync queues
        flushQueue(videoSyncQueue, videoStream)
        flushQueue(audioSyncQueue, audioStream)
    }
    
    // ========== Stage 1: Reordering ==========
    
    private fun processReorderQueues() {
        processReorderQueue(videoReorderQueue, videoSyncQueue, "video") { seq -> 
            nextVideoSeq = seq + 1 
        }
        processReorderQueue(audioReorderQueue, audioSyncQueue, "audio") { seq -> 
            nextAudioSeq = seq + 1 
        }
    }
    
    private inline fun processReorderQueue(
        reorderQueue: PriorityBlockingQueue<Frame>,
        syncQueue: PriorityBlockingQueue<Frame>,
        streamType: String,
        updateNextSeq: (Int) -> Unit
    ) {
        val nextExpected = if (streamType == "video") nextVideoSeq else nextAudioSeq
        
        while (reorderQueue.isNotEmpty()) {
            val frame = reorderQueue.peek() ?: break
            val now = System.currentTimeMillis()
            val age = now - frame.arrivalTime
            val seqGap = frame.sequenceNumber - nextExpected
            
            var shouldPromote = false
            var reason = ""
            
            when {
                // In-order or late arrival
                frame.sequenceNumber <= nextExpected -> {
                    shouldPromote = true
                    reason = if (frame.sequenceNumber == nextExpected) "in-order" else "late"
                    if (frame.sequenceNumber < nextExpected) {
                        reorderedFrames.incrementAndGet()
                    }
                }
                // Timeout - waited too long
                age > config.maxReorderDelayMs -> {
                    shouldPromote = true
                    reason = "timeout"
                    reorderedFrames.incrementAndGet()
                }
                // Large gap - probably lost packets
                seqGap > config.maxSequenceGap -> {
                    shouldPromote = true
                    reason = "gap"
                    reorderedFrames.incrementAndGet()
                }
                // Buffer overflow protection
                reorderQueue.size > config.maxSize -> {
                    shouldPromote = true
                    reason = "overflow"
                }
            }
            
            if (shouldPromote) {
                reorderQueue.poll()
                syncQueue.offer(frame)
                updateNextSeq(frame.sequenceNumber)
                logger.trace("{} frame promoted: seq={}, reason={}", streamType, frame.sequenceNumber, reason)
            } else {
                break // Wait for more frames
            }
        }
    }
    
    // ========== Stage 2: A/V Sync ==========
    
    private fun processSyncQueues() {
        // Initialize PTS bases
        videoSyncQueue.peek()?.let { if (videoBasePts == null) videoBasePts = it.pts }
        audioSyncQueue.peek()?.let { if (audioBasePts == null) audioBasePts = it.pts }
        
        // Interleave by normalized PTS
        while (videoSyncQueue.isNotEmpty() && audioSyncQueue.isNotEmpty()) {
            val video = videoSyncQueue.peek() ?: break
            val audio = audioSyncQueue.peek() ?: break
            
            val videoPtsNorm = video.pts - (videoBasePts ?: 0)
            val audioPtsNorm = audio.pts - (audioBasePts ?: 0)
            
            if (videoPtsNorm <= audioPtsNorm) {
                videoSyncQueue.poll()
                writeFrame(video, videoStream, "video")
                videoFramesWritten.incrementAndGet()
            } else {
                audioSyncQueue.poll()
                writeFrame(audio, audioStream, "audio")
                audioFramesWritten.incrementAndGet()
            }
        }
    }
    
    // ========== Stage 3: Stale Frame Handling ==========
    
    private fun flushStaleFrames() {
        val now = System.currentTimeMillis()
        val maxAge = config.maxReorderDelayMs * 2 // More lenient for sync stage
        
        // Flush stale video
        while (videoSyncQueue.isNotEmpty()) {
            val frame = videoSyncQueue.peek() ?: break
            if (now - frame.arrivalTime > maxAge) {
                videoSyncQueue.poll()
                writeFrame(frame, videoStream, "video")
                videoFramesWritten.incrementAndGet()
            } else break
        }
        
        // Flush stale audio
        while (audioSyncQueue.isNotEmpty()) {
            val frame = audioSyncQueue.peek() ?: break
            if (now - frame.arrivalTime > maxAge) {
                audioSyncQueue.poll()
                writeFrame(frame, audioStream, "audio")
                audioFramesWritten.incrementAndGet()
            } else break
        }
    }
    
    // ========== Write ==========
    
    private fun writeFrame(frame: Frame, stream: OutputStream, streamType: String) {
        try {
            stream.write(frame.data)
            stream.flush()
        } catch (e: IOException) {
            logger.error("Error writing {} frame", streamType, e)
        }
    }
    
    private fun drainQueueTo(from: PriorityBlockingQueue<Frame>, to: PriorityBlockingQueue<Frame>) {
        while (from.isNotEmpty()) {
            from.poll()?.let { to.offer(it) }
        }
    }
    
    private fun flushQueue(queue: PriorityBlockingQueue<Frame>, stream: OutputStream) {
        while (queue.isNotEmpty()) {
            queue.poll()?.let { frame ->
                try {
                    stream.write(frame.data)
                } catch (e: IOException) {
                    logger.error("Error flushing frame", e)
                }
            }
        }
        try { stream.flush() } catch (_: IOException) {}
    }
}
