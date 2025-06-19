package dev.marcelsoftware.apklink.sources

import dev.marcelsoftware.apklink.utils.XapkHandler
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ApkPureSource(private val packageName: String, private val version: String? = null) {
    private val logger = org.gradle.api.logging.Logging.getLogger(ApkPureSource::class.java)
    private val xapkHandler = XapkHandler()

    fun downloadApk(destinationDir: File): File {
        logger.lifecycle("Downloading app from APKPure: $packageName")

        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        val appInfo = getApkInfo()
        logger.info("Found app: ${appInfo.appName} v${appInfo.version}")

        val downloadFile = File(
            destinationDir,
            "${packageName}_${appInfo.version}${getFileExtension(appInfo.downloadUrl)}"
        )
        downloadFile(appInfo.downloadUrl, downloadFile)

        if (xapkHandler.isXapkFile(downloadFile)) {
            logger.lifecycle("Downloaded file is XAPK, extracting base APK...")
            val extractDir = File(destinationDir, "extracted-${downloadFile.nameWithoutExtension}")
            val baseApk = xapkHandler.extractBaseApk(downloadFile, extractDir)
            logger.lifecycle("Base APK extracted: ${baseApk.absolutePath}")
            return baseApk
        }

        logger.lifecycle("APK downloaded successfully: ${downloadFile.absolutePath}")
        return downloadFile
    }

    private fun getFileExtension(url: String): String {
        val lowercaseUrl = url.lowercase()
        return when {
            lowercaseUrl.contains(".xapk") -> ".xapk"
            else -> ".apk"
        }
    }

    private data class ApkInfo(
        val version: String,
        val downloadUrl: String,
        val appName: String
    )

    private fun getApkInfo(): ApkInfo {
        logger.info("Fetching app info for package: $packageName")

        val apiUrl = "https://tapi.pureapk.com/v3/get_app_his_version?package_name=$packageName&hl=en"
        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("Ual-Access-Businessid", "projecta")
        connection.setRequestProperty("Ual-Access-ProjectA", "{\"device_info\":{\"os_ver\":\"30\"}}")

        if (connection.responseCode != 200) {
            throw RuntimeException("Failed to fetch app details. HTTP code: ${connection.responseCode}")
        }

        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonResponse = JSONObject(responseText)
        val versionList = jsonResponse.getJSONArray("version_list")

        val versionVariant = if (version != null) {
            var found: JSONObject? = null

            for (i in 0 until versionList.length()) {
                val variant = versionList.getJSONObject(i)
                if (variant.getString("version_name") == version) {
                    found = variant
                    break
                }
            }

            found ?: throw RuntimeException("Version $version not found for $packageName")
        } else {
            if (versionList.length() == 0) {
                throw RuntimeException("No versions found for $packageName")
            }
            versionList.getJSONObject(0)
        }

        val appName = versionVariant.getString("title")
        val versionName = versionVariant.getString("version_name")
        val asset = versionVariant.getJSONObject("asset")
        val downloadUrl = asset.getString("url")

        return ApkInfo(
            version = versionName,
            downloadUrl = downloadUrl,
            appName = appName
        )
    }

    private fun downloadFile(url: String, destination: File) {
        logger.info("Downloading from $url to ${destination.absolutePath}")

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.instanceFollowRedirects = true

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw RuntimeException("Failed to download, HTTP code: $responseCode")
        }

        val contentLength = connection.contentLengthLong
        logger.info("File size: ${contentLength / 1024 / 1024} MB")

        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (contentLength > 0 && totalBytesRead - lastProgressUpdate > 5 * 1024 * 1024) {
                        val progress = (totalBytesRead * 100 / contentLength).toInt()
                        logger.lifecycle("Download progress: $progress% (${totalBytesRead / 1024 / 1024} MB / ${contentLength / 1024 / 1024} MB)")
                        lastProgressUpdate = totalBytesRead
                    }
                }
            }
        }

        if (!destination.exists() || destination.length() == 0L) {
            throw RuntimeException("Failed to download to ${destination.absolutePath}")
        }

        logger.info("Download complete: ${destination.length() / 1024 / 1024} MB")
    }
}

class ApkPureConfig {
    var packageName: String? = null
    var version: String? = null
}
