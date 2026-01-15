package com.thomas.espdoorbell.doorbell.streaming.transcoding

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class FFmpegProcessManager(
    private val config: StreamingProperties,
    private val videoPort: Int,
    private val audioPort: Int
) {
    private val logger = LoggerFactory.getLogger(FFmpegProcessManager::class.java)

    private var ffmpegProcess: Process? = null

    fun start(scope: CoroutineScope) {
        logger.info("Starting FFmpeg process with TCP inputs: video={}, audio={}", videoPort, audioPort)

        val command = buildCommand()
        logger.info("FFmpeg command: {}", command.joinToString(" "))

        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(false)
        ffmpegProcess = processBuilder.start()

        scope.launch {
            logFFmpegOutput()
        }
    }

    fun stop() {
        logger.info("Stopping FFmpeg process")

        ffmpegProcess?.let { process ->
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    logger.warn("FFmpeg did not stop gracefully, forcing termination")
                    process.destroyForcibly()
                }
            }
        }

        logger.info("FFmpeg process stopped")
    }

    fun isAlive(): Boolean = ffmpegProcess?.isAlive ?: false

    fun getStdoutStream(): InputStream? = ffmpegProcess?.inputStream

    private fun buildCommand(): List<String> {
        val clusterTimeMs = config.ffmpeg.clusterTimeMs

        return listOf(
            config.ffmpeg.path,

            "-f", "mjpeg",
            "-use_wallclock_as_timestamps", "1",
            "-analyzeduration", "0",
            "-probesize", "32",
            "-i", "tcp://127.0.0.1:$videoPort",

            "-f", "aac",
            "-use_wallclock_as_timestamps", "1",
            "-analyzeduration", "0",
            "-probesize", "32",
            "-i", "tcp://127.0.0.1:$audioPort",

            "-map", "0:v:0",
            "-map", "1:a:0",

            "-c:v", "libvpx",
            "-b:v", config.ffmpeg.videoBitrate,
            "-quality", "realtime",
            "-speed", "6",
            "-cpu-used", "5",
            "-deadline", "realtime",
            "-g", "30",
            "-keyint_min", "30",
            "-threads", "4",
            "-error-resilient", "1",
            "-lag-in-frames", "0",

            "-c:a", "libopus",
            "-b:a", config.ffmpeg.audioBitrate,
            "-ar", "48000",
            "-ac", "2",
            "-application", "lowdelay",

            "-fflags", "+genpts+nobuffer",
            "-avoid_negative_ts", "make_zero",

            "-f", "webm",
            "-cluster_size_limit", "256000",
            "-cluster_time_limit", clusterTimeMs.toString(),
            "-live", "1",
            "-dash", "1",
            "pipe:1"
        )
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
