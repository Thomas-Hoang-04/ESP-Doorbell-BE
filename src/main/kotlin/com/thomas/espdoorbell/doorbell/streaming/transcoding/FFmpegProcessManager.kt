package com.thomas.espdoorbell.doorbell.streaming.transcoding

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

/**
 * Manages FFmpeg process lifecycle for in-memory WebM streaming.
 * 
 * Pipeline:
 * - Creates named pipes for video (MJPEG) and audio (AAC) input
 * - Runs FFmpeg to transcode to VP8/Opus WebM
 * - Outputs fragmented WebM to stdout for in-memory parsing
 * 
 * The fragmented WebM stream is parsed by WebMStreamParser to extract
 * clusters which are sent to clients via WebSocket.
 */
// TODO: Add health monitoring and auto-restart on FFmpeg crash
// TODO: Add metrics for encoding performance (fps, bitrate, queue size)
class FFmpegProcessManager(
    private val workingDirectory: Path,
    private val config: StreamingProperties
) {
    private val logger = LoggerFactory.getLogger(FFmpegProcessManager::class.java)

    private lateinit var videoPipe: Path
    private lateinit var audioPipe: Path
    private var ffmpegProcess: Process? = null
    private var videoOutputStream: OutputStream? = null
    private var audioOutputStream: OutputStream? = null

    /**
     * Start FFmpeg process with named pipes for input and stdout for output.
     * 
     * @param scope CoroutineScope for background tasks
     */
    fun start(scope: CoroutineScope) {
        logger.info("Starting FFmpeg process in directory: {}", workingDirectory)

        // Create named pipes for input
        videoPipe = workingDirectory.resolve("video.pipe")
        audioPipe = workingDirectory.resolve("audio.pipe")
        createNamedPipe(videoPipe)
        createNamedPipe(audioPipe)
        logger.info("Created named pipes: video={}, audio={}", videoPipe, audioPipe)

        // Build FFmpeg command for fragmented WebM output to stdout
        val command = buildCommand()
        logger.info("Starting FFmpeg with command: {}", command.joinToString(" "))

        // Start FFmpeg process
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(false)
        ffmpegProcess = processBuilder.start()

        // Log FFmpeg stderr in background
        scope.launch {
            logFFmpegOutput()
        }

        // Open input pipes in background coroutines
        scope.launch(Dispatchers.IO) {
            try {
                videoOutputStream = Files.newOutputStream(videoPipe, StandardOpenOption.WRITE)
                logger.info("Video pipe opened for writing")
            } catch (e: IOException) {
                logger.error("Failed to open video pipe", e)
            }
        }

        scope.launch(Dispatchers.IO) {
            try {
                audioOutputStream = Files.newOutputStream(audioPipe, StandardOpenOption.WRITE)
                logger.info("Audio pipe opened for writing")
            } catch (e: IOException) {
                logger.error("Failed to open audio pipe", e)
            }
        }
    }

    /**
     * Stop FFmpeg process and cleanup resources
     */
    fun stop() {
        logger.info("Stopping FFmpeg process")

        // Close input streams
        try {
            videoOutputStream?.close()
            audioOutputStream?.close()
        } catch (e: IOException) {
            logger.error("Error closing streams", e)
        }

        // Stop FFmpeg process
        ffmpegProcess?.let { process ->
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    logger.warn("FFmpeg did not stop gracefully, forcing termination")
                    process.destroyForcibly()
                }
            }
        }

        // Cleanup pipes
        try {
            Files.deleteIfExists(videoPipe)
            Files.deleteIfExists(audioPipe)
        } catch (e: Exception) {
            logger.error("Error deleting pipes", e)
        }

        logger.info("FFmpeg process stopped")
    }

    /**
     * Get video output stream for writing MJPEG frames
     */
    fun getVideoOutputStream(): OutputStream? = videoOutputStream

    /**
     * Get audio output stream for writing AAC frames
     */
    fun getAudioOutputStream(): OutputStream? = audioOutputStream

    /**
     * Check if FFmpeg process is alive
     */
    fun isAlive(): Boolean = ffmpegProcess?.isAlive ?: false

    /**
     * Get the stdout stream for reading WebM output.
     * This stream contains fragmented WebM data to be parsed by WebMStreamParser.
     */
    fun getStdoutStream(): InputStream? = ffmpegProcess?.inputStream

    /**
     * Build FFmpeg command for fragmented WebM output to stdout.
     * Optimized for low-latency live streaming.
     */
    private fun buildCommand(): List<String> {
        return listOf(
            config.ffmpeg.path,
            
            // Video input: MJPEG from named pipe
            "-f", "mjpeg",
            "-use_wallclock_as_timestamps", "1",
            "-i", videoPipe.toString(),

            // Audio input: AAC from named pipe
            "-f", "aac",
            "-use_wallclock_as_timestamps", "1",
            "-i", audioPipe.toString(),

            // Map streams
            "-map", "0:v:0",
            "-map", "1:a:0",

            // VP8 video encoding - optimized for low latency
            "-c:v", "libvpx",
            "-b:v", config.ffmpeg.videoBitrate,
            "-quality", "realtime",
            "-speed", "6",
            "-cpu-used", "5",
            "-deadline", "realtime",
            "-g", "60",              // Keyframe every 2 seconds at 30fps
            "-keyint_min", "60",
            "-threads", "4",
            "-error-resilient", "1",
            "-lag-in-frames", "0",   // No lookahead for minimum latency

            // Opus audio encoding - optimized for low latency
            "-c:a", "libopus",
            "-b:a", config.ffmpeg.audioBitrate,
            "-ar", "48000",
            "-ac", "2",
            "-application", "lowdelay",

            // Timestamp handling
            "-fflags", "+genpts+nobuffer",
            "-avoid_negative_ts", "make_zero",

            // Output: fragmented WebM to stdout
            "-f", "webm",
            "-cluster_size_limit", "256000",  // ~256KB max per cluster
            "-cluster_time_limit", "2000",    // ~2 second clusters
            "-live", "1",
            "-dash", "1",                     // DASH-compatible fragmented output
            "pipe:1"                          // Output to stdout
        )
    }

    private fun createNamedPipe(pipe: Path) {
        try {
            // Delete existing pipe if present
            Files.deleteIfExists(pipe)
            
            val processBuilder = ProcessBuilder("mkfifo", pipe.toString())
            val process = processBuilder.start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IOException("Failed to create named pipe: $pipe (exit code: $exitCode)")
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while creating pipe", e)
        }
    }

    private fun logFFmpegOutput() {
        ffmpegProcess?.let { process ->
            try {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        logger.debug("FFmpeg: {}", line)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error reading FFmpeg output", e)
            }
        }
    }
}
