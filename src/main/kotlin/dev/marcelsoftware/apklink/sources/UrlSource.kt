package dev.marcelsoftware.apklink.sources

import org.gradle.api.logging.Logging
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class UrlConfig {
    var url: String? = null
    var fileName: String? = null
}

class UrlSource(private val url: String, private val customFileName: String? = null) {
    private val logger = Logging.getLogger(UrlSource::class.java)

    fun downloadApk(destinationDir: File): File {
        logger.lifecycle("Downloading APK from URL: $url")

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val fileName = customFileName ?: inferFileName(url) ?: "downloaded.apk"
        val outputFile = File(destinationDir, fileName)

        downloadFile(url, outputFile)

        logger.lifecycle("APK downloaded successfully: ${outputFile.absolutePath}")
        return outputFile
    }

    private fun inferFileName(url: String): String? {
        try {
            val urlPath = URL(url).path
            val lastSegment = urlPath.substringAfterLast('/')
            if (lastSegment.isNotEmpty() && lastSegment.lowercase().endsWith(".apk")) {
                return lastSegment
            }
        } catch (e: Exception) {
            logger.warn("Failed to infer filename from URL: ${e.message}")
        }
        return null
    }

    private fun downloadFile(url: String, destination: File) {
        logger.info("Downloading file from $url to ${destination.absolutePath}")

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw RuntimeException("Failed to download, HTTP code: $responseCode")
            }

            val contentLength = connection.contentLengthLong
            logger.info("File size: ${contentLength / 1024} KB")

            connection.inputStream.use { input ->
                Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            if (!destination.exists() || destination.length() == 0L) {
                throw RuntimeException("Failed to download APK to ${destination.absolutePath}")
            }

            logger.info("Download complete: ${destination.length()} bytes")
        } catch (e: Exception) {
            logger.error("Download failed", e)
            throw RuntimeException("Failed to download from URL: ${e.message}")
        }
    }
}
