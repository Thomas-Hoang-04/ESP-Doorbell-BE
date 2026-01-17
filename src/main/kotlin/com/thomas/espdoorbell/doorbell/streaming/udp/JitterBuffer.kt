package com.thomas.espdoorbell.doorbell.streaming.udp

import org.slf4j.LoggerFactory
import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JitterBuffer(
    private val maxSize: Int = 10,
    private val maxWaitMs: Long = 50,
    private val maxDriftMs: Long = 500
) {
    private val log = LoggerFactory.getLogger(JitterBuffer::class.java)
    private val lock = ReentrantLock()

    private val videoState = StreamState("video")
    private val audioState = StreamState("audio")

    private inner class StreamState(val name: String) {
        val buffer = PriorityQueue<UdpStreamPacket>(compareBy { it.pts })
        var expectedPts: Long = 0
        var lastPts: Long = 0
        val insertionTimes = mutableMapOf<Long, Long>()

        fun insert(packet: UdpStreamPacket) {
            if (buffer.size >= maxSize) {
                val dropped = buffer.poll()
                if (dropped != null) {
                    log.warn("JitterBuffer overflow, dropping oldest {} pts={}", name, dropped.pts)
                    insertionTimes.remove(dropped.pts)
                }
            }
            buffer.add(packet)
            insertionTimes[packet.pts] = System.currentTimeMillis()
        }

        fun flush(): List<UdpStreamPacket> {
            val ready = mutableListOf<UdpStreamPacket>()
            val now = System.currentTimeMillis()
            while (buffer.isNotEmpty()) {
                val head = buffer.peek() ?: break
                val insertTime = insertionTimes[head.pts] ?: now
                val isExpired = (now - insertTime) >= maxWaitMs
                val isExpected = head.pts >= expectedPts
                val isOverflow = buffer.size > maxSize
                
                if (isExpected || isExpired || isOverflow) {
                    val packet = buffer.poll() ?: break
                    insertionTimes.remove(packet.pts)
                    ready.add(packet)
                    expectedPts = packet.pts + 1
                    lastPts = packet.pts
                } else {
                    break
                }
            }
            return ready
        }

        fun reset() {
            buffer.clear()
            insertionTimes.clear()
            expectedPts = 0
            lastPts = 0
        }
    }

    fun insertVideo(packet: UdpStreamPacket): List<UdpStreamPacket> = lock.withLock {
        videoState.insert(packet)
        checkDrift()
        return videoState.flush()
    }

    fun insertAudio(packet: UdpStreamPacket): List<UdpStreamPacket> = lock.withLock {
        audioState.insert(packet)
        checkDrift()
        return audioState.flush()
    }

    private fun checkDrift() {
        if (videoState.lastPts == 0L || audioState.lastPts == 0L) return
        
        val drift = kotlin.math.abs(videoState.lastPts - audioState.lastPts)
        if (drift > maxDriftMs) {
            log.warn("A/V drift {}ms exceeded threshold {}ms, resetting buffers", drift, maxDriftMs)
            reset()
        }
    }

    fun reset() = lock.withLock {
        val droppedVideo = videoState.buffer.size
        val droppedAudio = audioState.buffer.size

        videoState.reset()
        audioState.reset()

        if (droppedVideo > 0 || droppedAudio > 0) {
            log.info("JitterBuffer reset: dropped {} video, {} audio packets", droppedVideo, droppedAudio)
        }
    }
}
