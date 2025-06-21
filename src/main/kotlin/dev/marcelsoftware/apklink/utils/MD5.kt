package dev.marcelsoftware.apklink.utils

import java.io.File
import java.security.MessageDigest

fun calculateMD5(file: File): String {
    val md = MessageDigest.getInstance("MD5")
    return file.inputStream().use { fis ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
