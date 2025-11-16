package com.thomas.espdoorbell.doorbell.streaming.service.transcoding

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

/**
 * Manages FFmpeg process lifecycle including:
 * - Creating named pipes for video and audio input
 * - Starting FFmpeg process with proper configuration
 * - Managing pipe output streams
 * - Logging FFmpeg output
 * - Cleanup and shutdown
 */
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
     * Start FFmpeg process with named pipes
     */
    fun start(scope: CoroutineScope) {
        logger.info("Starting FFmpeg process in directory: $workingDirectory")

        // Create named pipes
        videoPipe = workingDirectory.resolve("video.pipe")
        audioPipe = workingDirectory.resolve("audio.pipe")
        createNamedPipe(videoPipe)
        createNamedPipe(audioPipe)
        logger.info("Created named pipes: $videoPipe, $audioPipe")

        // Build FFmpeg command
        val segmentPattern = workingDirectory.resolve("segment_%05d.webm").toString()
        val command = buildFFmpegCommand(segmentPattern)

        logger.info("Starting FFmpeg with command: ${command.joinToString(" ")}")

        // Start FFmpeg process
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(false)
        ffmpegProcess = processBuilder.start()

        // Log FFmpeg output in background
        scope.launch {
            logFFmpegOutput()
        }

        // Open pipes in background coroutines
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

        // Close streams
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
     * Get video output stream for writing frames
     */
    fun getVideoOutputStream(): OutputStream? = videoOutputStream

    /**
     * Get audio output stream for writing frames
     */
    fun getAudioOutputStream(): OutputStream? = audioOutputStream

    /**
     * Check if FFmpeg process is alive
     */
    fun isAlive(): Boolean = ffmpegProcess?.isAlive ?: false

    private fun buildFFmpegCommand(segmentPattern: String): List<String> {
        return listOf(
            config.ffmpeg.path,
            "-f", "mjpeg",
            "-use_wallclock_as_timestamps", "1",
            "-i", videoPipe.toString(),

            "-f", "aac",
            "-use_wallclock_as_timestamps", "1",
            "-i", audioPipe.toString(),

            "-map", "0:v:0",
            "-map", "1:a:0",

            "-c:v", "libvpx",
            "-b:v", config.ffmpeg.videoBitrate,
            "-quality", "realtime",
            "-speed", "6",
            "-cpu-used", "5",
            "-deadline", "realtime",
            "-g", "60",
            "-keyint_min", "60",
            "-threads", "4",
            "-error-resilient", "1",
            "-lag-in-frames", "0",

            "-c:a", "libopus",
            "-b:a", config.ffmpeg.audioBitrate,
            "-ar", "48000",
            "-ac", "2",
            "-application", "lowdelay",

            "-fflags", "+genpts+igndts",
            "-avoid_negative_ts", "make_zero",
            "-max_muxing_queue_size", "1024",

            "-f", "segment",
            "-segment_time", config.ffmpeg.segmentDuration.toString(),
            "-segment_format", "webm",
            "-segment_format_options", "live=1",
            "-reset_timestamps", "1",
            "-break_non_keyframes", "0",

            segmentPattern
        )
    }

    private fun createNamedPipe(pipe: Path) {
        try {
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
                        logger.debug("FFmpeg: $line")
                    }
                }
            } catch (e: Exception) {
                logger.error("Error reading FFmpeg output", e)
            }
        }
    }
}

