package com.thomas.espdoorbell.doorbell.streaming.pipeline

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.getLastModifiedTime

/**
 * Handles streaming resource lifecycle:
 * - Graceful shutdown of all pipelines
 * - Startup cleanup of stale pipe directories from crashes
 * - Scheduled cleanup of orphaned working directories
 * 
 * Working directories contain only named pipes (video.pipe, audio.pipe)
 * used for FFmpeg input - no WebM segment files.
 */
@Service
class StreamCleanupService(
    private val deviceStreamManager: DeviceStreamManager,
    private val config: StreamingProperties
) {
    private val logger = LoggerFactory.getLogger(StreamCleanupService::class.java)
    
    companion object {
        private val STALE_THRESHOLD = Duration.ofMinutes(30)
    }

    /**
     * On startup, clean any stale directories from previous crashes
     */
    @PostConstruct
    fun onStartup() {
        logger.info("Streaming cleanup service starting - checking for stale directories")
        cleanupStaleDirectories(maxAge = Duration.ZERO) // Clean all orphaned directories on startup
    }

    /**
     * On shutdown, stop all active pipelines gracefully
     */
    @PreDestroy
    fun onShutdown() {
        logger.info("Shutting down - stopping all streaming pipelines")
        runBlocking {
            try {
                deviceStreamManager.stopAll()
                cleanupWorkingDirectory()
                logger.info("All pipelines stopped and cleaned up")
            } catch (e: Exception) {
                logger.error("Error during shutdown cleanup", e)
            }
        }
    }

    /**
     * Periodic cleanup of orphaned directories (every 30 minutes)
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    fun scheduledCleanup() {
        cleanupStaleDirectories(maxAge = STALE_THRESHOLD)
    }

    /**
     * Clean directories older than maxAge that don't belong to active pipelines.
     * If maxAge is Duration.ZERO, cleans ALL orphaned directories regardless of age.
     */
    private fun cleanupStaleDirectories(maxAge: Duration) {
        val workingDir = Paths.get(config.workingDirectory)
        
        if (!workingDir.exists() || !workingDir.isDirectory()) {
            return
        }

        val activeDeviceIds = deviceStreamManager.getActiveDeviceIds().map { it.toString() }.toSet()
        val now = Instant.now()
        var cleaned = 0

        try {
            workingDir.listDirectoryEntries().forEach { deviceDir ->
                if (!deviceDir.isDirectory()) return@forEach
                
                val dirName = deviceDir.fileName.toString()
                
                // Skip if this is an active pipeline
                if (dirName in activeDeviceIds) return@forEach
                
                // Check age (skip check if maxAge is zero - startup cleanup)
                if (maxAge != Duration.ZERO) {
                    val lastModified = deviceDir.getLastModifiedTime().toInstant()
                    val age = Duration.between(lastModified, now)
                    if (age < maxAge) return@forEach
                }
                
                // Clean this orphaned directory
                cleanupDeviceDirectory(deviceDir)
                cleaned++
            }
            
            if (cleaned > 0) {
                logger.info("Cleaned {} orphaned pipe directories", cleaned)
            }
        } catch (e: Exception) {
            logger.error("Error during directory cleanup", e)
        }
    }

    /**
     * Clean a single device directory (delete pipes and directory)
     */
    private fun cleanupDeviceDirectory(deviceDir: Path) {
        try {
            // Delete pipe files
            deviceDir.resolve("video.pipe").deleteIfExists()
            deviceDir.resolve("audio.pipe").deleteIfExists()
            
            // Delete any other leftover files
            deviceDir.listDirectoryEntries().forEach { it.deleteIfExists() }
            
            // Delete the directory itself
            deviceDir.deleteIfExists()
            
            logger.debug("Cleaned up directory: {}", deviceDir)
        } catch (e: Exception) {
            logger.warn("Failed to cleanup directory {}: {}", deviceDir, e.message)
        }
    }

    /**
     * Clean entire working directory on shutdown
     */
    private fun cleanupWorkingDirectory() {
        val workingDir = Paths.get(config.workingDirectory)
        if (workingDir.exists()) {
            try {
                workingDir.listDirectoryEntries().forEach { deviceDir ->
                    if (deviceDir.isDirectory()) {
                        cleanupDeviceDirectory(deviceDir)
                    }
                }
                logger.info("Working directory cleaned")
            } catch (e: Exception) {
                logger.warn("Error cleaning working directory: {}", e.message)
            }
        }
    }
}
