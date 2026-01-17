package com.thomas.espdoorbell.doorbell.streaming.udp

data class UdpStreamPacket(
    val magic: Int,
    val type: Byte,
    val flags: Byte,
    val fragmentIndex: Int,
    val pts: Long,
    val seq: Long,
    val payload: ByteArray
) {
    val isStart: Boolean get() = (flags.toInt() and FLAG_START.toInt()) != 0
    val isEnd: Boolean get() = (flags.toInt() and FLAG_END.toInt()) != 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UdpStreamPacket
        return seq == other.seq && type == other.type
    }

    override fun hashCode(): Int {
        var result = seq.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    companion object {
        const val MAGIC = 0x5544
        const val HEADER_SIZE = 13

        const val TYPE_VIDEO: Byte = 0x01
        const val TYPE_AUDIO: Byte = 0x02
        const val TYPE_AUTH: Byte = 0xFE.toByte()
        const val TYPE_CONTROL: Byte = 0xFF.toByte()

        const val CTRL_AUTH_OK: Byte = 0x01
        const val CTRL_AUTH_FAIL: Byte = 0x02
        const val CTRL_STREAM_END: Byte = 0x10
        const val CTRL_KEEPALIVE: Byte = 0x20

        const val FLAG_START: Byte = 0x80.toByte()
        const val FLAG_END: Byte = 0x40.toByte()

        fun parse(data: ByteArray): UdpStreamPacket? {
            if (data.size < HEADER_SIZE) return null

            val magic = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
            if (magic != MAGIC) return null

            val type = data[2]
            val flags = data[3]
            val fragmentIndex = data[4].toInt() and 0xFF

            val pts = ((data[5].toLong() and 0xFF) shl 24) or
                    ((data[6].toLong() and 0xFF) shl 16) or
                    ((data[7].toLong() and 0xFF) shl 8) or
                    (data[8].toLong() and 0xFF)

            val seq = ((data[9].toLong() and 0xFF) shl 24) or
                    ((data[10].toLong() and 0xFF) shl 16) or
                    ((data[11].toLong() and 0xFF) shl 8) or
                    (data[12].toLong() and 0xFF)

            val payload = if (data.size > HEADER_SIZE) {
                data.copyOfRange(HEADER_SIZE, data.size)
            } else {
                ByteArray(0)
            }

            return UdpStreamPacket(magic, type, flags, fragmentIndex, pts, seq, payload)
        }

        fun createControlPacket(controlType: Byte): ByteArray {
            return byteArrayOf(
                ((MAGIC shr 8) and 0xFF).toByte(),
                (MAGIC and 0xFF).toByte(),
                TYPE_CONTROL,
                controlType,
                0x00,
                0, 0, 0, 0,
                0, 0, 0, 0
            )
        }
    }
}
