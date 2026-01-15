package com.thomas.espdoorbell.doorbell.streaming.websocket.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class StreamPacket(
    val type: PacketType,
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
        if (ptsMillis != other.ptsMillis) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + ptsMillis
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        const val MAGIC_NUMBER: Short = 0x4156 // "AV"
        const val HEADER_SIZE = 8
    }
}

private val packetLogger = org.slf4j.LoggerFactory.getLogger("StreamPacketParser")

fun ByteBuffer.parseStreamPacket(): StreamPacket? {
    order(ByteOrder.BIG_ENDIAN)

    if (remaining() < StreamPacket.HEADER_SIZE) {
        packetLogger.warn("Packet too small: {} bytes (need {})", remaining(), StreamPacket.HEADER_SIZE)
        return null
    }

    val pos = position()
    val firstBytes = ByteArray(minOf(16, remaining()))
    get(firstBytes)
    position(pos)
    packetLogger.info("First bytes (hex): {}", firstBytes.joinToString(" ") { "%02X".format(it) })

    val magicNumber = short
    val typeByte = get()
    val flags = get()
    val ptsMillis = int

    packetLogger.info("Magic=0x{}, Expected=0x{}, Type={}, PTS={}", 
        magicNumber.toInt().and(0xFFFF).toString(16).uppercase(),
        StreamPacket.MAGIC_NUMBER.toString(16).uppercase(),
        typeByte, ptsMillis)

    if (magicNumber != StreamPacket.MAGIC_NUMBER) {
        packetLogger.warn("Magic number mismatch!")
        return null
    }

    val type = StreamPacket.PacketType.fromByte(typeByte) ?: run {
        packetLogger.warn("Unknown packet type: {}", typeByte)
        return null
    }

    val payloadSize = remaining()
    val payload = ByteArray(payloadSize)
    get(payload)

    return StreamPacket(
        type = type,
        ptsMillis = ptsMillis,
        payload = payload
    )
}

