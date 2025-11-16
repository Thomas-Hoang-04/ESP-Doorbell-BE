package com.thomas.espdoorbell.doorbell.streaming.service

import com.thomas.espdoorbell.doorbell.streaming.config.StreamingProperties
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * Service responsible for cleaning up streaming resources
 * Handles graceful shutdown and scheduled cleanup of orphaned directories
 */
@Service
class StreamCleanupService(
    private val deviceStreamManager: DeviceStreamManager,
    private val config: StreamingProperties
) {
    private val logger = LoggerFactory.getLogger(StreamCleanupService::class.java)

    /**
     * Called on application shutdown
     * Stops all active pipelines gracefully
     */
    @PreDestroy
    fun shutdown() {
        logger.info("Application shutting down - stopping all streaming pipelines")
        runBlocking {
            try {
                deviceStreamManager.stopAll()
                logger.info("All pipelines stopped successfully")
            } catch (e: Exception) {
                logger.error("Error stopping pipelines during shutdown", e)
            }
        }
    }

    /**
     * Scheduled cleanup of orphaned stream directories
     * Runs every hour to remove directories that are no longer in use
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    fun cleanupOrphanedDirectories() {
        logger.info("Starting scheduled cleanup of orphaned stream directories")
        
        try {
            val workingDir = Paths.get(config.workingDirectory)
            
            if (!Files.exists(workingDir) || !Files.isDirectory(workingDir)) {
                logger.debug("Working directory does not exist or is not a directory: $workingDir")
                return
            }

            val activeDeviceIds = deviceStreamManager.getActiveDeviceIds()
            val now = Instant.now()
            var deletedCount = 0

            Files.list(workingDir).use { stream ->
                stream.filter { path -> Files.isDirectory(path) }
                    .forEach { deviceDir ->
                        try {
                            val deviceDirName = deviceDir.fileName.toString()
                            
                            // Check if directory belongs to an active device
                            val isActive = activeDeviceIds.any { it.toString() == deviceDirName }
                            
                            if (!isActive) {
                                // Check if directory is old enough to delete (older than 1 hour)
                                val lastModified = Files.getLastModifiedTime(deviceDir).toInstant()
                                val ageInHours = ChronoUnit.HOURS.between(lastModified, now)
                                
                                if (ageInHours >= 1) {
                                    logger.info("Deleting orphaned directory: $deviceDir (age: $ageInHours hours)")
                                    deleteDirectory(deviceDir)
                                    deletedCount++
                                } else {
                                    logger.debug("Orphaned directory $deviceDir is too recent (age: $ageInHours hours)")
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Error processing directory: $deviceDir", e)
                        }
                    }
            }

            if (deletedCount > 0) {
                logger.info("Cleanup completed - deleted $deletedCount orphaned directories")
            } else {
                logger.debug("Cleanup completed - no orphaned directories found")
            }
        } catch (e: Exception) {
            logger.error("Error during scheduled cleanup", e)
        }
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private fun deleteDirectory(path: Path) {
        try {
            if (Files.isDirectory(path)) {
                // Delete all files in directory first
                path.listDirectoryEntries().forEach { child ->
                    if (Files.isDirectory(child)) {
                        deleteDirectory(child)
                    } else {
                        Files.deleteIfExists(child)
                    }
                }
            }
            // Delete the directory itself
            Files.deleteIfExists(path)
        } catch (e: Exception) {
            logger.error("Error deleting directory: $path", e)
            throw e
        }
    }
}

