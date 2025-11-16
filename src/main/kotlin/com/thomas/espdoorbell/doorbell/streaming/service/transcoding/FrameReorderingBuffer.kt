package com.thomas.espdoorbell.doorbell.streaming.service.transcoding

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.PriorityBlockingQueue

/**
 * Generic frame reordering buffer that handles out-of-order packets
 * Uses sequence numbers to reorder frames and applies smart logic for:
 * - In-order delivery when possible
 * - Timeout-based forwarding for delayed packets
 * - Gap detection for lost packets
 * - Buffer overflow protection
 */
class FrameReorderingBuffer(
    private val config: StreamingProperties.BufferSettings,
    private val streamType: String, // "video" or "audio"
    private val writeFrame: (TimedFrame) -> Unit
) {
    private val logger = LoggerFactory.getLogger(FrameReorderingBuffer::class.java)

    private val buffer = PriorityBlockingQueue<TimedFrame>(100) { a, b ->
        a.sequenceNumber.compareTo(b.sequenceNumber)
    }

    private var nextExpectedSequence = 0
    private var initialized = false

    @Volatile
    private var running = false

    data class TimedFrame(
        val data: ByteArray,
        val pts: Long,       // In microseconds
        val dts: Long,       // In microseconds
        val sequenceNumber: Int,
        val arrivalTime: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TimedFrame

            if (!data.contentEquals(other.data)) return false
            if (pts != other.pts) return false
            if (dts != other.dts) return false
            if (sequenceNumber != other.sequenceNumber) return false
            if (arrivalTime != other.arrivalTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + pts.hashCode()
            result = 31 * result + dts.hashCode()
            result = 31 * result + sequenceNumber
            result = 31 * result + arrivalTime.hashCode()
            return result
        }
    }

    /**
     * Offer a frame to the reordering buffer
     * Returns true if accepted, false if buffer is full
     */
    fun offer(data: ByteArray, pts: Long, dts: Long, sequenceNumber: Int): Boolean {
        // Initialize on first frame
        if (!initialized) {
            nextExpectedSequence = sequenceNumber
            initialized = true
            logger.info("$streamType stream initialized at sequence: $sequenceNumber")
        }

        val frame = TimedFrame(data, pts, dts, sequenceNumber)

        if (!buffer.offer(frame)) {
            logger.warn("$streamType buffer full, dropping frame seq=$sequenceNumber")
            return false
        }

        // Detect out-of-order or gaps
        if (sequenceNumber < nextExpectedSequence) {
            logger.debug("Out-of-order $streamType frame: expected seq >= $nextExpectedSequence, got $sequenceNumber")
        } else if (sequenceNumber > nextExpectedSequence) {
            val gap = sequenceNumber - nextExpectedSequence
            logger.debug("$streamType sequence gap detected: expected $nextExpectedSequence, got $sequenceNumber (gap=$gap)")
        }

        return true
    }

    /**
     * Start processing frames from the buffer
     * Runs in a coroutine and continuously processes frames based on reordering logic
     */
    suspend fun startProcessing(scope: CoroutineScope) {
        running = true
        scope.launch {
            processBuffer()
        }
    }

    /**
     * Get current buffer size
     */
    fun size(): Int = buffer.size

    /**
     * Flush all remaining frames in the buffer
     */
    fun flush() {
        logger.info("Flushing $streamType buffer - ${buffer.size} frames remaining")
        while (!buffer.isEmpty()) {
            buffer.poll()?.let { frame ->
                writeFrame(frame)
            }
        }
    }

    /**
     * Stop processing
     */
    fun stop() {
        running = false
    }

    private suspend fun processBuffer() {
        while (running) {
            try {
                val frame = buffer.peek()

                if (frame == null) {
                    delay(10)
                    continue
                }

                val now = System.currentTimeMillis()
                val frameAge = now - frame.arrivalTime
                val sequenceGap = frame.sequenceNumber - nextExpectedSequence

                var shouldWrite = false
                var reason = ""

                // Write if sequence number matches or is older (late arrival)
                if (frame.sequenceNumber <= nextExpectedSequence) {
                    shouldWrite = true
                    reason = if (frame.sequenceNumber == nextExpectedSequence) "in-order" else "late-arrival"

                    if (frame.sequenceNumber < nextExpectedSequence) {
                        logger.debug("Writing late $streamType frame: seq=${frame.sequenceNumber}, expected=$nextExpectedSequence")
                    }
                }
                // Force write if waited too long
                else if (frameAge > config.maxReorderDelayMs) {
                    shouldWrite = true
                    reason = "timeout"
                    logger.debug("$streamType frame timeout - forcing write. Gap: $sequenceGap")
                }
                // Force write if gap is too large (probably lost packets)
                else if (sequenceGap > config.maxSequenceGap) {
                    shouldWrite = true
                    reason = "large-gap"
                    logger.debug("$streamType large sequence gap detected: $sequenceGap")
                }
                // Force write if buffer is too full
                else if (buffer.size > config.maxSize) {
                    shouldWrite = true
                    reason = "buffer-full"
                    logger.debug("$streamType buffer overflow protection")
                }

                if (shouldWrite) {
                    buffer.poll()
                    writeFrame(frame)

                    // Update expected sequence
                    // If we had a gap, jump to next expected sequence
                    if (frame.sequenceNumber >= nextExpectedSequence) {
                        nextExpectedSequence = frame.sequenceNumber + 1
                    }

                    logger.debug(
                        "$streamType frame written: seq=${frame.sequenceNumber}, " +
                                "pts=${frame.pts / 1000}ms, reason=$reason, buffer=${buffer.size}"
                    )
                } else {
                    delay(10)
                }
            } catch (e: Exception) {
                logger.error("Error in $streamType reordering", e)
            }
        }
    }
}

