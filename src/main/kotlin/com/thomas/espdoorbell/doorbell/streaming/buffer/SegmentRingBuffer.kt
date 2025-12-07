package com.thomas.espdoorbell.doorbell.streaming.buffer

import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.SegmentData

/**
 * Thread-safe circular buffer that maintains the last N segments for late-joining clients.
 * When a new Android client connects, they receive these buffered segments first for catch-up,
 * then switch to the live segment stream.
 */
class SegmentRingBuffer(private val capacity: Int = 5) {
    private val buffer = ArrayDeque<SegmentData>(capacity)
    
    /**
     * Add a new segment to the buffer.
     * If buffer is at capacity, the oldest segment is dropped.
     */
    @Synchronized
    fun add(segment: SegmentData) {
        if (buffer.size >= capacity) {
            buffer.removeFirst()
        }
        buffer.addLast(segment)
    }
    
    /**
     * Get a copy of all buffered segments for a late-joining client.
     * Returns segments in chronological order (oldest first).
     */
    @Synchronized
    fun getBufferedSegments(): List<SegmentData> = buffer.toList()
    
    /**
     * Get the number of segments currently buffered.
     */
    @Synchronized
    fun size(): Int = buffer.size
    
    /**
     * Clear all buffered segments.
     */
    @Synchronized
    fun clear() {
        buffer.clear()
    }
    
    /**
     * Check if the buffer is empty.
     */
    @Synchronized
    fun isEmpty(): Boolean = buffer.isEmpty()
}
