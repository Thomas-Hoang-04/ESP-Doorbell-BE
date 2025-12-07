package com.thomas.espdoorbell.doorbell.streaming.transcoding

import com.thomas.espdoorbell.doorbell.streaming.buffer.BackpressureChannel
import com.thomas.espdoorbell.doorbell.streaming.buffer.SegmentRingBuffer
import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.SegmentData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Parses WebM stream from FFmpeg stdout and extracts clusters as segments.
 * 
 * WebM uses EBML (Extensible Binary Meta Language) format where:
 * - The header contains codec info (must be sent to late-joiners first)
 * - Clusters contain ~2 seconds of A/V data each
 * 
 * This parser detects cluster boundaries (EBML ID 0x1F43B675) and emits
 * complete clusters as segments for streaming to clients.
 */
class WebMStreamParser(
    private val ringBuffer: SegmentRingBuffer,
    private val channel: BackpressureChannel
) {
    private val logger = LoggerFactory.getLogger(WebMStreamParser::class.java)
    
    companion object {
        // WebM/EBML element IDs
        private val CLUSTER_ID = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75)
        private val EBML_HEADER_ID = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        
        private const val READ_BUFFER_SIZE = 8192
    }
    
    // Accumulates bytes until we find cluster boundaries
    private val accumulator = ByteArrayOutputStream(256 * 1024)
    
    // The initialization segment (EBML header + track info) - required for playback
    @Volatile
    var initSegment: ByteArray? = null
        private set
    
    private var segmentIndex = 0
    
    @Volatile
    private var running = false
    
    /**
     * Start parsing the FFmpeg stdout stream.
     * Runs in a coroutine, reading bytes and emitting segments.
     */
    fun startParsing(inputStream: InputStream, scope: CoroutineScope) {
        running = true
        
        scope.launch(Dispatchers.IO) {
            logger.info("Starting WebM stream parsing")
            
            try {
                val reader = BufferedInputStream(inputStream, READ_BUFFER_SIZE)
                val chunk = ByteArray(READ_BUFFER_SIZE)
                
                while (running && isActive) {
                    val bytesRead = reader.read(chunk)
                    if (bytesRead == -1) {
                        logger.info("FFmpeg stream ended")
                        break
                    }
                    
                    if (bytesRead > 0) {
                        processBytes(chunk, bytesRead)
                    }
                }
                
                // Emit any remaining data as final segment
                emitRemainingData()
                
            } catch (e: Exception) {
                if (running) {
                    logger.error("Error parsing WebM stream", e)
                }
            } finally {
                logger.info("WebM stream parsing stopped. Emitted $segmentIndex segments.")
            }
        }
    }
    
    /**
     * Stop parsing.
     */
    fun stop() {
        running = false
    }
    
    /**
     * Reset parser state for a new stream.
     */
    fun reset() {
        accumulator.reset()
        initSegment = null
        segmentIndex = 0
    }
    
    private suspend fun processBytes(data: ByteArray, length: Int) {
        accumulator.write(data, 0, length)
        val bytes = accumulator.toByteArray()
        
        var lastClusterEnd = 0
        var pos = 0
        
        // Scan for cluster boundaries
        while (pos <= bytes.size - 4) {
            if (isClusterStart(bytes, pos)) {
                if (initSegment == null && pos > 0) {
                    // First cluster found - everything before is the init segment
                    initSegment = bytes.copyOfRange(0, pos)
                    logger.info("Init segment captured: ${initSegment!!.size} bytes")
                    lastClusterEnd = pos
                } else if (pos > lastClusterEnd && initSegment != null) {
                    // Complete cluster ready to emit
                    emitSegment(bytes.copyOfRange(lastClusterEnd, pos))
                    lastClusterEnd = pos
                }
            }
            pos++
        }
        
        // Keep unprocessed data in accumulator
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
    
    private suspend fun emitSegment(data: ByteArray) {
        val segment = SegmentData(
            index = segmentIndex++,
            timestamp = System.currentTimeMillis(),
            data = data
        )
        
        logger.debug("Emitting segment ${segment.index}: ${data.size} bytes")
        
        // Add to ring buffer for late-comers
        ringBuffer.add(segment)
        
        // Send to live subscribers
        channel.send(segment)
    }
    
    private fun emitRemainingData() {
        if (accumulator.size() > 0 && initSegment != null) {
            val remaining = accumulator.toByteArray()
            if (remaining.isNotEmpty()) {
                val segment = SegmentData(
                    index = segmentIndex++,
                    timestamp = System.currentTimeMillis(),
                    data = remaining
                )
                ringBuffer.add(segment)
                channel.trySend(segment)
                logger.info("Emitted final segment: ${remaining.size} bytes")
            }
        }
    }
}
