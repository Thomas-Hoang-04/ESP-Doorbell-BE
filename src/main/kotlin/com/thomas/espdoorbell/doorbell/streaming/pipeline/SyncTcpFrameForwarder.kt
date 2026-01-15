package com.thomas.espdoorbell.doorbell.streaming.pipeline

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong

class SyncTcpFrameForwarder(
    private val config: StreamingProperties
) {
    private val logger = LoggerFactory.getLogger(SyncTcpFrameForwarder::class.java)

    data class Frame(
        val data: ByteArray,
        val pts: Long,
        val isVideo: Boolean,
        val arrivalTime: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return pts == other.pts && isVideo == other.isVideo
        }
        override fun hashCode(): Int = 31 * pts.hashCode() + isVideo.hashCode()
    }

    private val videoQueue = PriorityBlockingQueue<Frame>(100) { a, b ->
        a.pts.compareTo(b.pts)
    }
    private val audioQueue = PriorityBlockingQueue<Frame>(100) { a, b ->
        a.pts.compareTo(b.pts)
    }

    private var videoServerSocket: ServerSocket? = null
    private var audioServerSocket: ServerSocket? = null
    private var videoSocket: Socket? = null
    private var audioSocket: Socket? = null
    private var videoOutputStream: OutputStream? = null
    private var audioOutputStream: OutputStream? = null

    @Volatile private var videoBasePts: Long? = null
    @Volatile private var audioBasePts: Long? = null

    private val videoFramesWritten = AtomicLong(0)
    private val audioFramesWritten = AtomicLong(0)

    @Volatile private var running = false

    val videoPort: Int
        get() = videoServerSocket?.localPort ?: 0

    val audioPort: Int
        get() = audioServerSocket?.localPort ?: 0

    fun start(scope: CoroutineScope) {
        logger.info("Starting SyncTcpFrameForwarder")

        val configVideoPort = config.tcp.videoPort
        val configAudioPort = config.tcp.audioPort

        videoServerSocket = ServerSocket(configVideoPort)
        audioServerSocket = ServerSocket(configAudioPort)

        logger.info("TCP sockets opened: video={}, audio={}", videoPort, audioPort)

        running = true

        scope.launch(Dispatchers.IO) {
            try {
                logger.info("Waiting for FFmpeg to connect to video port {}", videoPort)
                videoSocket = videoServerSocket?.accept()
                videoOutputStream = videoSocket?.getOutputStream()
                logger.info("FFmpeg connected to video socket")
            } catch (e: IOException) {
                if (running) logger.error("Error accepting video connection", e)
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                logger.info("Waiting for FFmpeg to connect to audio port {}", audioPort)
                audioSocket = audioServerSocket?.accept()
                audioOutputStream = audioSocket?.getOutputStream()
                logger.info("FFmpeg connected to audio socket")
            } catch (e: IOException) {
                if (running) logger.error("Error accepting audio connection", e)
            }
        }

        scope.launch(Dispatchers.IO) {
            runZipperLoop()
        }
    }

    fun stop() {
        running = false
        logger.info("Stopping SyncTcpFrameForwarder. Stats: video={}, audio={}",
            videoFramesWritten.get(), audioFramesWritten.get())

        try {
            videoOutputStream?.close()
            audioOutputStream?.close()
            videoSocket?.close()
            audioSocket?.close()
            videoServerSocket?.close()
            audioServerSocket?.close()
        } catch (e: IOException) {
            logger.error("Error closing sockets", e)
        }
    }

    fun offerVideo(data: ByteArray, pts: Long) {
        if (!running) return
        if (videoQueue.size >= config.sync.maxQueueSize) {
            videoQueue.poll()
        }
        videoQueue.offer(Frame(data, pts, isVideo = true))
    }

    fun offerAudio(data: ByteArray, pts: Long) {
        if (!running) return
        if (audioQueue.size >= config.sync.maxQueueSize) {
            audioQueue.poll()
        }
        audioQueue.offer(Frame(data, pts, isVideo = false))
    }

    private suspend fun runZipperLoop() {
        logger.info("Zipper loop started")

        while (running) {
            val vOut = videoOutputStream
            val aOut = audioOutputStream

            // If neither is connected, wait
            if (vOut == null && aOut == null) {
                delay(10)
                continue
            }

            val video = videoQueue.peek()
            val audio = audioQueue.peek()

            // Mode 1: Both connected - Sync logic
            if (vOut != null && aOut != null) {
                when {
                    video != null && audio != null -> {
                        if (videoBasePts == null) videoBasePts = video.pts
                        if (audioBasePts == null) audioBasePts = audio.pts

                        val videoPtsNorm = video.pts - (videoBasePts ?: 0)
                        val audioPtsNorm = audio.pts - (audioBasePts ?: 0)

                        if (videoPtsNorm <= audioPtsNorm) {
                            videoQueue.poll()
                            writeFrame(video, vOut, "video")
                            videoFramesWritten.incrementAndGet()
                        } else {
                            audioQueue.poll()
                            writeFrame(audio, aOut, "audio")
                            audioFramesWritten.incrementAndGet()
                        }
                    }
                    video != null -> {
                        // Only video available in queue
                        val age = System.currentTimeMillis() - video.arrivalTime
                        if (age > config.sync.maxSyncWaitMs) {
                            videoQueue.poll()
                            if (videoBasePts == null) videoBasePts = video.pts
                            writeFrame(video, vOut, "video")
                            videoFramesWritten.incrementAndGet()
                        } else {
                            delay(5)
                        }
                    }
                    audio != null -> {
                        // Only audio available in queue
                        val age = System.currentTimeMillis() - audio.arrivalTime
                        if (age > config.sync.maxSyncWaitMs) {
                            audioQueue.poll()
                            if (audioBasePts == null) audioBasePts = audio.pts
                            writeFrame(audio, aOut, "audio")
                            audioFramesWritten.incrementAndGet()
                        } else {
                            delay(5)
                        }
                    }
                    else -> delay(5)
                }
            } 
            // Mode 2: Only Video connected (Fix for deadlock)
            else if (vOut != null) {
                if (video != null) {
                    if (videoBasePts == null) videoBasePts = video.pts
                    videoQueue.poll()
                    writeFrame(video, vOut, "video")
                    videoFramesWritten.incrementAndGet()
                } else {
                    delay(5)
                }
            }
            // Mode 3: Only Audio connected (Unlikely, but symmetric)
            else if (aOut != null) {
                if (audio != null) {
                    if (audioBasePts == null) audioBasePts = audio.pts
                    audioQueue.poll()
                    writeFrame(audio, aOut, "audio")
                    audioFramesWritten.incrementAndGet()
                } else {
                    delay(5)
                }
            }
        }

        logger.info("Zipper loop stopped")
    }

    private fun writeFrame(frame: Frame, stream: OutputStream, streamType: String) {
        try {
            stream.write(frame.data)
            stream.flush()
        } catch (e: IOException) {
            logger.error("Error writing {} frame", streamType, e)
        }
    }
}
