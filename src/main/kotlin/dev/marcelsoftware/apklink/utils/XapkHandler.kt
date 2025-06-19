package dev.marcelsoftware.apklink.utils

import org.gradle.api.logging.Logging
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

class XapkHandler {
    private val logger = Logging.getLogger(XapkHandler::class.java)

    fun extractBaseApk(xapkFile: File, outputDir: File): File {
        logger.lifecycle("Extracting base APK from XAPK: ${xapkFile.absolutePath}")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val extractDir = File(outputDir, "extracted")
        if (!extractDir.exists()) {
            extractDir.mkdirs()
        } else {
            extractDir.listFiles()?.forEach { it.delete() }
        }

        unzip(xapkFile, extractDir)

        val manifestFile = File(extractDir, "manifest.json")
        if (!manifestFile.exists()) {
            throw RuntimeException("Invalid XAPK file: manifest.json not found")
        }

        val manifest = JSONObject(manifestFile.readText())
        val packageName = manifest.getString("package_name")

        val baseApk = File(extractDir, "$packageName.apk")
        if (!baseApk.exists()) {
            val apkFiles = extractDir.listFiles { file ->
                file.name.endsWith(".apk") && file.name != "config.apk"
            }

            if (apkFiles.isNullOrEmpty()) {
                throw RuntimeException("No base APK found in XAPK file")
            }

            val copyToFile = File(outputDir, "$packageName.apk")
            apkFiles[0].copyTo(copyToFile, overwrite = true)
            return copyToFile
        }

        val outputApk = File(outputDir, baseApk.name)
        baseApk.copyTo(outputApk, overwrite = true)

        logger.lifecycle("Successfully extracted base APK: ${outputApk.absolutePath}")
        return outputApk
    }

    private fun unzip(zipFile: File, destDir: File) {
        logger.info("Unzipping ${zipFile.absolutePath} to ${destDir.absolutePath}")

        ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
            var entry = zipIn.nextEntry

            while (entry != null) {
                val newFile = File(destDir, entry.name)

                val parent = newFile.parentFile
                if (!parent.exists()) {
                    parent.mkdirs()
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    FileOutputStream(newFile).use { fileOut ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int

                        while (zipIn.read(buffer).also { bytesRead = it } != -1) {
                            fileOut.write(buffer, 0, bytesRead)
                        }
                    }
                }

                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        logger.info("Unzip completed")
    }

    fun isXapkFile(file: File): Boolean {
        return file.name.lowercase().endsWith(".xapk")
    }
}
