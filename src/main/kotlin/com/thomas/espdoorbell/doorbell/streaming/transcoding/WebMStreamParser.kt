package com.thomas.espdoorbell.doorbell.streaming.transcoding

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class WebMStreamRelay {
    private val logger = LoggerFactory.getLogger(WebMStreamRelay::class.java)

    companion object {
        private val CLUSTER_ID = byteArrayOf(0x1F, 0x43, 0xB6.toByte(), 0x75)
        private const val READ_BUFFER_SIZE = 8192
    }

    private val accumulator = ByteArrayOutputStream(256 * 1024)

    @Volatile
    var initSegment: ByteArray? = null
        private set

    private val _clusterFlow = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 10)
    val clusterFlow: SharedFlow<ByteArray> = _clusterFlow.asSharedFlow()

    private var clusterCount = 0

    @Volatile
    private var running = false

    fun startRelaying(inputStream: InputStream, scope: CoroutineScope) {
        running = true

        scope.launch(Dispatchers.IO) {
            logger.info("Starting WebM stream relay")

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

                emitRemainingData()

            } catch (e: Exception) {
                if (running) {
                    logger.error("Error relaying WebM stream", e)
                }
            } finally {
                logger.info("WebM stream relay stopped. Emitted {} clusters.", clusterCount)
            }
        }
    }

    fun stop() {
        running = false
    }

    fun reset() {
        accumulator.reset()
        initSegment = null
        clusterCount = 0
    }

    private suspend fun processBytes(data: ByteArray, length: Int) {
        accumulator.write(data, 0, length)
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

    private suspend fun emitRemainingData() {
        if (accumulator.size() > 0 && initSegment != null) {
            val remaining = accumulator.toByteArray()
            if (remaining.isNotEmpty()) {
                clusterCount++
                _clusterFlow.emit(remaining)
                logger.info("Emitted final cluster: {} bytes", remaining.size)
            }
        }
    }
}
