package com.thomas.espdoorbell.doorbell.streaming.service.transcoding

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import com.thomas.espdoorbell.doorbell.streaming.model.SegmentData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.TimeUnit

/**
 * Watches a directory for new WebM segment files and broadcasts them to subscribers
 * Maintains a circular buffer of recent segments for late joiners
 */
class SegmentBroadcaster(
    private val workingDirectory: Path,
    private val config: StreamingProperties.SegmentSettings
) {
    private val logger = LoggerFactory.getLogger(SegmentBroadcaster::class.java)

    private val _segmentFlow = MutableSharedFlow<SegmentData>(replay = config.bufferCount)
    private val segmentBuffer = mutableListOf<SegmentData>()
    private var segmentIndex = 0

    @Volatile
    private var running = false

    /**
     * Subscribe to the segment flow
     * New subscribers will receive buffered segments via the replay mechanism
     */
    fun subscribeToSegments(): SharedFlow<SegmentData> = _segmentFlow.asSharedFlow()

    /**
     * Get currently buffered segments for late joiners
     */
    fun getBufferedSegments(): List<SegmentData> {
        synchronized(segmentBuffer) {
            return segmentBuffer.toList()
        }
    }

    /**
     * Start watching for segment files
     */
    suspend fun start(scope: CoroutineScope) {
        running = true
        scope.launch {
            watchAndBroadcastSegments()
        }
        logger.info("Segment broadcaster started for directory: $workingDirectory")
    }

    /**
     * Stop watching for segments
     */
    fun stop() {
        running = false
        logger.info("Segment broadcaster stopped")
    }

    private suspend fun watchAndBroadcastSegments() {
        try {
            val watchService = FileSystems.getDefault().newWatchService()
            workingDirectory.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )

            val processedFiles = mutableSetOf<String>()

            while (running) {
                val key = watchService.poll(100, TimeUnit.MILLISECONDS) ?: continue

                for (event in key.pollEvents()) {
                    val filePath = workingDirectory.resolve(event.context() as Path)
                    val fileName = filePath.fileName.toString()

                    if (fileName.endsWith(".webm") && !processedFiles.contains(fileName)) {
                        // Wait for file to be stable (fully written)
                        waitForFileStable(filePath)

                        // Read segment bytes
                        val segmentBytes = withContext(Dispatchers.IO) {
                            Files.readAllBytes(filePath)
                        }

                        logger.info("Broadcasting segment: $fileName (${segmentBytes.size} bytes)")

                        val segmentData = SegmentData(
                            index = segmentIndex++,
                            timestamp = System.currentTimeMillis(),
                            data = segmentBytes
                        )

                        // Emit to flow
                        _segmentFlow.emit(segmentData)

                        // Add to circular buffer
                        synchronized(segmentBuffer) {
                            segmentBuffer.add(segmentData)
                            if (segmentBuffer.size > config.bufferCount) {
                                segmentBuffer.removeAt(0)
                            }
                        }

                        processedFiles.add(fileName)

                        // Cleanup old segments
                        if (segmentIndex > config.cleanupThreshold) {
                            val oldFile = String.format("segment_%05d.webm", segmentIndex - config.cleanupThreshold)
                            val oldPath = workingDirectory.resolve(oldFile)
                            withContext(Dispatchers.IO) {
                                Files.deleteIfExists(oldPath)
                            }
                            processedFiles.remove(oldFile)
                            logger.debug("Deleted old segment: $oldFile")
                        }
                    }
                }

                key.reset()
            }

            watchService.close()
        } catch (e: Exception) {
            logger.error("Error in segment watcher", e)
        }
    }

    private suspend fun waitForFileStable(filePath: Path) {
        var lastSize = 0L
        var stableCount = 0

        while (stableCount < 3) {
            delay(50)
            try {
                val currentSize = withContext(Dispatchers.IO) {
                    Files.size(filePath)
                }
                if (currentSize == lastSize && currentSize > 0) {
                    stableCount++
                } else {
                    stableCount = 0
                }
                lastSize = currentSize
            } catch (e: Exception) {
                // File might not be fully created yet
                stableCount = 0
            }
        }
    }
}

