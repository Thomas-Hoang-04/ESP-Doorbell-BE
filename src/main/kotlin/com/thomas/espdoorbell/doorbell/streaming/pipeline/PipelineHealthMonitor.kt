package com.thomas.espdoorbell.doorbell.streaming.pipeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Monitors health of streaming pipelines and FFmpeg processes.
 * Performs periodic health checks and triggers restarts when issues are detected.
 * 
 * Health monitoring includes:
 * - FFmpeg process aliveness
 * - Frame throughput (detecting stalls)
 * - Memory usage
 * - Error rate tracking
 */
// TODO: Implement health check callbacks
// TODO: Add metrics export (Prometheus/Micrometer)
// TODO: Implement auto-restart with backoff
// TODO: Add alerting integration
class PipelineHealthMonitor(
    private val checkInterval: Duration = 5.seconds,
    private val maxRestartAttempts: Int = 3
) {
    private val logger = LoggerFactory.getLogger(PipelineHealthMonitor::class.java)
    
    // Health state per pipeline
    data class PipelineHealth(
        val deviceId: UUID,
        var lastFrameTime: Long = System.currentTimeMillis(),
        var frameCount: Long = 0,
        var errorCount: Int = 0,
        var restartCount: Int = 0,
        var isHealthy: Boolean = true
    )
    
    private val pipelines = ConcurrentHashMap<UUID, PipelineHealth>()
    
    @Volatile
    private var running = false
    
    /**
     * Start the health monitoring loop.
     */
    fun start(scope: CoroutineScope) {
        running = true
        logger.info("Starting pipeline health monitor (interval: $checkInterval)")
        
        scope.launch(Dispatchers.IO) {
            while (running && isActive) {
                try {
                    checkAllPipelines()
                } catch (e: Exception) {
                    logger.error("Error during health check", e)
                }
                delay(checkInterval)
            }
        }
    }
    
    /**
     * Stop the health monitor.
     */
    fun stop() {
        running = false
        logger.info("Stopping pipeline health monitor")
    }
    
    /**
     * Register a pipeline for monitoring.
     */
    fun registerPipeline(deviceId: UUID) {
        pipelines[deviceId] = PipelineHealth(deviceId)
        logger.info("Registered pipeline for monitoring: $deviceId")
    }
    
    /**
     * Unregister a pipeline from monitoring.
     */
    fun unregisterPipeline(deviceId: UUID) {
        pipelines.remove(deviceId)
        logger.info("Unregistered pipeline from monitoring: $deviceId")
    }
    
    /**
     * Record a frame received (for throughput monitoring).
     */
    fun recordFrame(deviceId: UUID) {
        pipelines[deviceId]?.let { health ->
            health.lastFrameTime = System.currentTimeMillis()
            health.frameCount++
        }
    }
    
    /**
     * Record an error for a pipeline.
     */
    fun recordError(deviceId: UUID) {
        pipelines[deviceId]?.let { health ->
            health.errorCount++
        }
    }
    
    /**
     * Get health status for a pipeline.
     */
    fun getHealth(deviceId: UUID): PipelineHealth? = pipelines[deviceId]
    
    /**
     * Get all unhealthy pipelines.
     */
    fun getUnhealthyPipelines(): List<PipelineHealth> {
        return pipelines.values.filter { !it.isHealthy }
    }
    
    private fun checkAllPipelines() {
        val now = System.currentTimeMillis()
        val staleThresholdMs = 10_000 // 10 seconds without frames = stale
        
        pipelines.values.forEach { health ->
            val timeSinceLastFrame = now - health.lastFrameTime
            val wasHealthy = health.isHealthy
            
            // Check if frames are stale
            health.isHealthy = timeSinceLastFrame < staleThresholdMs
            
            if (wasHealthy && !health.isHealthy) {
                logger.warn("Pipeline ${health.deviceId} became unhealthy (no frames for ${timeSinceLastFrame}ms)")
                // TODO: Trigger restart callback
            }
        }
    }
}
