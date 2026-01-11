package com.thomas.espdoorbell.doorbell.streaming.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "streaming")
data class StreamingProperties(
    var ffmpeg: FfmpegSettings = FfmpegSettings(),
    var tcp: TcpSettings = TcpSettings(),
    var sync: SyncSettings = SyncSettings(),
    var websocketBaseUrl: String = "ws://localhost:8080"
) {
    data class FfmpegSettings(
        var path: String = "ffmpeg",
        var videoBitrate: String = "1M",
        var audioBitrate: String = "128k",
        var clusterTimeMs: Int = 500
    )

    data class TcpSettings(
        var videoPort: Int = 0,
        var audioPort: Int = 0
    )

    data class SyncSettings(
        var maxSyncWaitMs: Long = 200,
        var maxQueueSize: Int = 50
    )
}
