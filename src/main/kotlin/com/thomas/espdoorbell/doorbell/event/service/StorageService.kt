package com.thomas.espdoorbell.doorbell.event.service

import com.thomas.espdoorbell.doorbell.core.config.AppProperties
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Service
class StorageService(
    private val appProperties: AppProperties
) {
    fun saveEventImage(eventId: UUID, imageBytes: ByteArray): String {
        val eventDir = Paths.get(appProperties.storage.uploadDir, "events", eventId.toString())
        Files.createDirectories(eventDir)
        
        val fileName = "${System.currentTimeMillis()}.jpg"
        val filePath = eventDir.resolve(fileName)
        Files.write(filePath, imageBytes)
        
        // Return relative path for URL generation
        return "uploads/events/$eventId/$fileName"
    }
}
