package com.thomas.espdoorbell.doorbell.streaming.pipeline

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class PipelineHealthTracker {
    private val logger = LoggerFactory.getLogger(PipelineHealthTracker::class.java)

    data class PipelineStats(
        val deviceId: UUID,
        var lastFrameTimeMs: Long = System.currentTimeMillis(),
        var frameCount: Long = 0
    )

    private val pipelines = ConcurrentHashMap<UUID, PipelineStats>()

    fun registerPipeline(deviceId: UUID) {
        pipelines[deviceId] = PipelineStats(deviceId)
        logger.debug("Registered pipeline for health tracking: {}", deviceId)
    }

    fun unregisterPipeline(deviceId: UUID) {
        pipelines.remove(deviceId)
        logger.debug("Unregistered pipeline from health tracking: {}", deviceId)
    }

    fun recordFrame(deviceId: UUID) {
        pipelines[deviceId]?.apply {
            lastFrameTimeMs = System.currentTimeMillis()
            frameCount++
        }
    }

    fun getStalePipelines(staleThresholdMs: Long = 15_000): List<UUID> {
        val now = System.currentTimeMillis()
        return pipelines.values
            .filter { (now - it.lastFrameTimeMs) > staleThresholdMs }
            .map { it.deviceId }
    }

    fun getStats(deviceId: UUID): PipelineStats? = pipelines[deviceId]
}
