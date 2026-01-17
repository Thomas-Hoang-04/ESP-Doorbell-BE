package com.thomas.espdoorbell.doorbell.streaming.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "streaming")
data class StreamingProperties(
    var gstreamer: GStreamerSettings = GStreamerSettings(),
    var websocketBaseUrl: String = "ws://localhost:8080",
    var udp: UdpSettings = UdpSettings(),
    var dtls: DtlsSettings = DtlsSettings()
) {
    data class GStreamerSettings(
        var videoBitrate: String = "1M",
        var audioBitrate: String = "128k",
        var cpuUsed: Int = 5,
        var opusFrameSizeMs: Int = 20,
        var keyframeMaxDist: Int = 30
    )

    data class UdpSettings(
        var port: Int = 8443
    )

    data class DtlsSettings(
        var certPath: String = "",
        var keyPath: String = ""
    )
}
