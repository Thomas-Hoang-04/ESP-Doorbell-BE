package com.thomas.espdoorbell.doorbell.streaming.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class StreamPacket(
    val type: PacketType,
    val sequenceNumber: Int,
    val ptsMillis: Int,
    val payload: ByteArray
) {
    enum class PacketType(val value: Byte) {
        VIDEO(0x01),
        AUDIO(0x02);

        companion object {
            fun fromByte(value: Byte): PacketType? {
                return entries.find { it.value == value }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StreamPacket

        if (type != other.type) return false
        if (sequenceNumber != other.sequenceNumber) return false
        if (ptsMillis != other.ptsMillis) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + ptsMillis
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val MAGIC_NUMBER: Short = 0x4156 // "AV"
        const val HEADER_SIZE = 12
    }
}

/**
 * Extension function to parse StreamPacket from ByteBuffer
 * Returns null if packet is invalid
 */
fun ByteBuffer.parseStreamPacket(): StreamPacket? {
    // Ensure big-endian byte order
    order(ByteOrder.BIG_ENDIAN)

    // Validate minimum packet size (header is 12 bytes)
    if (remaining() < StreamPacket.HEADER_SIZE) {
        return null
    }

    // Parse header (12 bytes)
    val magicNumber = short              // Offset 0-1
    val typeByte = get()                 // Offset 2
    val flags = get()                    // Offset 3 (reserved)
    val sequenceNumber = int             // Offset 4-7
    val ptsMillis = int                  // Offset 8-11

    // Validate magic number
    if (magicNumber != StreamPacket.MAGIC_NUMBER) {
        return null
    }

    // Parse packet type
    val type = StreamPacket.PacketType.fromByte(typeByte) ?: return null

    // Extract payload
    val payloadSize = remaining()
    val payload = ByteArray(payloadSize)
    get(payload)

    return StreamPacket(
        type = type,
        sequenceNumber = sequenceNumber,
        ptsMillis = ptsMillis,
        payload = payload
    )
}

