package com.thomas.espdoorbell.doorbell.streaming.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Metadata for WebM segments sent to Android clients
 * Sent as JSON text message before the binary WebM data
 */
data class SegmentMetadata(
    val type: String = "segment",
    val index: Int,
    val timestamp: Long,
    val size: Int
)

/**
 * Container for segment data and metadata
 */
data class SegmentData(
    val index: Int,
    val timestamp: Long,
    val data: ByteArray
) {
    fun toMetadata(): SegmentMetadata {
        return SegmentMetadata(
            index = index,
            timestamp = timestamp,
            size = data.size
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SegmentData

        if (index != other.index) return false
        if (timestamp != other.timestamp) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

