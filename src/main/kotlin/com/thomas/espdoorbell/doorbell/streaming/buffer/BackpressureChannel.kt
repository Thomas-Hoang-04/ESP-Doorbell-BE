package com.thomas.espdoorbell.doorbell.streaming.buffer

import com.thomas.espdoorbell.doorbell.streaming.websocket.protocol.SegmentData
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Bounded channel wrapper that provides backpressure handling for segment delivery.
 * When the buffer is full, oldest segments are dropped to prevent memory buildup.
 */
// TODO: Add metrics for dropped segments
// TODO: Add configurable overflow strategy
class BackpressureChannel(
    capacity: Int = 10
) {
    private val channel = Channel<SegmentData>(
        capacity = capacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /**
     * Send a segment to the channel.
     * Non-blocking - if buffer is full, oldest segment is dropped.
     */
    suspend fun send(segment: SegmentData) {
        channel.send(segment)
    }
    
    /**
     * Try to send without suspending.
     * Returns true if sent, false if channel is closed.
     */
    fun trySend(segment: SegmentData): Boolean {
        return channel.trySend(segment).isSuccess
    }
    
    /**
     * Get a Flow of segments for consumption.
     */
    fun asFlow(): Flow<SegmentData> = channel.receiveAsFlow()
    
    /**
     * Close the channel.
     */
    fun close() {
        channel.close()
    }
    
    /**
     * Check if channel is closed.
     */
    @OptIn(DelicateCoroutinesApi::class)
    val isClosedForSend: Boolean
        get() = channel.isClosedForSend
}
