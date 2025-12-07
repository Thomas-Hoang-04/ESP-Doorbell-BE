package com.thomas.espdoorbell.doorbell.streaming.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "streaming")
data class StreamingProperties(
    var ffmpeg: FfmpegSettings = FfmpegSettings(),
    var buffer: BufferSettings = BufferSettings(),
    var segment: SegmentSettings = SegmentSettings(),
    var workingDirectory: String = "./streams"
) {
    data class FfmpegSettings(
        var path: String = "ffmpeg",
        var videoBitrate: String = "1M",
        var audioBitrate: String = "128k"
    )

    data class BufferSettings(
        var maxSize: Int = 50,
        var maxReorderDelayMs: Long = 500,
        var maxSequenceGap: Int = 10
    )

    data class SegmentSettings(
        var bufferCount: Int = 5  // Ring buffer size for late-joiners
    )
}
