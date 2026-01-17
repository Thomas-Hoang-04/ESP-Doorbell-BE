package com.thomas.espdoorbell.doorbell.streaming.udp

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class FragmentAssembler {
    private val log = LoggerFactory.getLogger(FragmentAssembler::class.java)
    
    private val frameBuffers = ConcurrentHashMap<Long, MutableMap<Int, ByteArray>>()
    private val frameConfigs = ConcurrentHashMap<Long, FrameConfig>()

    private data class FrameConfig(
        val pts: Long,
        var totalFragments: Int? = null,
        var lastReceivedTime: Long = System.currentTimeMillis()
    )

    fun assemble(packet: UdpStreamPacket): ByteArray? {
        val pts = packet.pts
        
        if (packet.isStart && packet.isEnd) {
            return packet.payload
        }
        
        val fragments = frameBuffers.computeIfAbsent(pts) { ConcurrentHashMap<Int, ByteArray>() }
        val config = frameConfigs.computeIfAbsent(pts) { FrameConfig(pts) }
        config.lastReceivedTime = System.currentTimeMillis()
        
        fragments[packet.fragmentIndex] = packet.payload
        
        if (packet.isEnd) {
            config.totalFragments = packet.fragmentIndex + 1
        }
        
        val total = config.totalFragments
        if (total != null && fragments.size == total) {
            for (i in 0 until total) {
                if (!fragments.containsKey(i)) return null
            }
            
            val totalSize = (0 until total).sumOf { fragments[it]?.size ?: 0 }
            val assembledFrame = ByteArray(totalSize)
            var offset = 0
            for (i in 0 until total) {
                val data = fragments[i]!!
                System.arraycopy(data, 0, assembledFrame, offset, data.size)
                offset += data.size
            }
            
            frameBuffers.remove(pts)
            frameConfigs.remove(pts)
            return assembledFrame
        }
        
        if (frameConfigs.size > 100) {
            cleanup()
        }
        
        return null
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val timeoutMs = 1000 
        
        val it = frameConfigs.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now - entry.value.lastReceivedTime > timeoutMs) {
                frameBuffers.remove(entry.key)
                it.remove()
                log.warn("Dropped stale/incomplete fragmented frame at PTS {}", entry.key)
            }
        }
    }
}
