package dev.marcelsoftware.apklink

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import com.googlecode.d2j.dex.Dex2jar
import com.googlecode.d2j.reader.BaseDexFileReader
import com.googlecode.d2j.reader.MultiDexFileReader
import com.googlecode.dex2jar.tools.BaksmaliBaseDexExceptionHandler
import dev.marcelsoftware.apklink.sources.ApkPureConfig
import dev.marcelsoftware.apklink.sources.ApkPureSource
import dev.marcelsoftware.apklink.sources.UrlConfig
import dev.marcelsoftware.apklink.sources.UrlSource
import dev.marcelsoftware.apklink.utils.calculateMD5
import java.io.File
import java.nio.file.Files
import org.json.JSONObject

open class ApkLinkConfig {
    var apkFile: File? = null
    var outputFile: File? = null
    var apkPureConfig: ApkPureConfig? = null
    var urlConfig: UrlConfig? = null
    var enabled: Boolean = true
    var forceReprocess: Boolean = false

    fun apkpure(action: ApkPureConfig.() -> Unit) {
        apkPureConfig = ApkPureConfig().apply(action)
    }

    fun url(action: UrlConfig.() -> Unit) {
        urlConfig = UrlConfig().apply(action)
    }
}

abstract class ProcessApkTask : DefaultTask() {
    @get:InputFile
    abstract val apkFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun processApk() {
        val inputFile = apkFile.get().asFile
        val outputFile = outputFile.get().asFile

        logger.lifecycle("Starting APK processing task")
        logger.info("Input APK: ${inputFile.absolutePath}")
        logger.info("Output JAR: ${outputFile.absolutePath}")

        if (!inputFile.exists()) {
            logger.error("Input APK file does not exist: ${inputFile.absolutePath}")
            throw RuntimeException("APK file not found: ${inputFile.absolutePath}")
        }

        if (!inputFile.canRead()) {
            logger.error("Cannot read input APK file: ${inputFile.absolutePath}")
            throw RuntimeException("Cannot read APK file: ${inputFile.absolutePath}")
        }

        logger.info("APK file validation passed")
        logger.debug("APK file size: ${inputFile.length()} bytes")

        val outputDir = outputFile.parentFile
        logger.debug("Creating output directory: ${outputDir.absolutePath}")

        if (!outputDir.exists()) {
            val created = outputDir.mkdirs()
            if (created) {
                logger.info("Created output directory: ${outputDir.absolutePath}")
            } else {
                logger.warn("Failed to create output directory or it already exists: ${outputDir.absolutePath}")
            }
        } else {
            logger.debug("Output directory already exists: ${outputDir.absolutePath}")
        }

        if (outputFile.exists()) {
            logger.info("Output file already exists, will be overwritten: ${outputFile.absolutePath}")
            logger.debug("Existing output file size: ${outputFile.length()} bytes")
        }

        try {
            logger.lifecycle("Converting APK to JAR using dex2jar...")
            val startTime = System.currentTimeMillis()

            val reader: BaseDexFileReader = MultiDexFileReader.open(Files.readAllBytes(inputFile.toPath()))
            val handler = BaksmaliBaseDexExceptionHandler()

            Dex2jar.from(reader)
                .withExceptionHandler(handler)
                .reUseReg(false)
                .topoLogicalSort()
                .skipDebug(true)
                .optimizeSynchronized(false)
                .printIR(false)
                .noCode(false)
                .skipExceptions(true)
                .to(outputFile.toPath())

            val duration = System.currentTimeMillis() - startTime
            logger.lifecycle("APK conversion completed successfully in ${duration}ms")

            if (!outputFile.exists()) {
                logger.error("Output JAR file was not created: ${outputFile.absolutePath}")
                throw RuntimeException("Failed to create output JAR file")
            }

            logger.info("Output JAR file created successfully")
            logger.info("Output JAR size: ${outputFile.length()} bytes")

            saveApkHash(inputFile, outputFile)

        } catch (e: Exception) {
            logger.error("Failed to convert APK to JAR", e)
            logger.error("Error details: ${e.message}")
            throw RuntimeException("APK conversion failed: ${e.message}", e)
        }

        logger.lifecycle("APK processing task completed successfully")
    }

    private fun saveApkHash(inputFile: File, outputFile: File) {
        val hashFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.md5")
        val md5 = calculateMD5(inputFile)
        hashFile.writeText(md5)
        logger.debug("Saved APK hash ($md5) to ${hashFile.absolutePath}")
    }
}

data class ConfigSnapshot(
    val apkFilePath: String?,
    val outputFilePath: String?,
    val apkPurePackage: String?,
    val apkPureVersion: String?,
    val urlSource: String?,
    val urlFileName: String?
) {
    companion object {
        fun from(config: ApkLinkConfig): ConfigSnapshot {
            return ConfigSnapshot(
                apkFilePath = config.apkFile?.absolutePath,
                outputFilePath = config.outputFile?.absolutePath,
                apkPurePackage = config.apkPureConfig?.packageName,
                apkPureVersion = config.apkPureConfig?.version,
                urlSource = config.urlConfig?.url,
                urlFileName = config.urlConfig?.fileName
            )
        }

        fun fromJson(json: JSONObject): ConfigSnapshot {
            return ConfigSnapshot(
                apkFilePath = if (json.has("apkFilePath") && !json.isNull("apkFilePath")) json.getString("apkFilePath") else null,
                outputFilePath = if (json.has("outputFilePath") && !json.isNull("outputFilePath")) json.getString("outputFilePath") else null,
                apkPurePackage = if (json.has("apkPurePackage") && !json.isNull("apkPurePackage")) json.getString("apkPurePackage") else null,
                apkPureVersion = if (json.has("apkPureVersion") && !json.isNull("apkPureVersion")) json.getString("apkPureVersion") else null,
                urlSource = if (json.has("urlSource") && !json.isNull("urlSource")) json.getString("urlSource") else null,
                urlFileName = if (json.has("urlFileName") && !json.isNull("urlFileName")) json.getString("urlFileName") else null
            )
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        apkFilePath?.let { json.put("apkFilePath", it) }
        outputFilePath?.let { json.put("outputFilePath", it) }
        apkPurePackage?.let { json.put("apkPurePackage", it) }
        apkPureVersion?.let { json.put("apkPureVersion", it) }
        urlSource?.let { json.put("urlSource", it) }
        urlFileName?.let { json.put("urlFileName", it) }
        return json
    }
}

class ApkLinkPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val logger = target.logger

        logger.info("Applying APK Link plugin to project: ${target.name}")

        val extension = target.extensions.create("apklink", ApkLinkConfig::class.java)
        logger.debug("Created apklink extension")

        val processApkTask = target.tasks.register("processApk", ProcessApkTask::class.java) { task ->
            task.group = "apklink"
            task.description = "Converts APK to JAR and adds as dependency"
            logger.debug("Registered processApk task")
        }

        target.afterEvaluate {
            logger.info("Evaluating apklink configuration for project: ${target.name}")

            if (!extension.enabled) {
                logger.info("ApkLink plugin is disabled for this project")
                return@afterEvaluate
            }

            if (extension.apkFile == null && extension.apkPureConfig == null && extension.urlConfig == null) {
                logger.info("No APK source configuration found, skipping ApkLink setup")
                return@afterEvaluate
            }

            val defaultOutputDir = File(target.buildDir, "apklink")
            val defaultOutputName = when {
                extension.apkFile != null -> extension.apkFile!!.nameWithoutExtension
                extension.apkPureConfig != null -> "apkpure-${extension.apkPureConfig!!.packageName}-${extension.apkPureConfig!!.version ?: "latest"}"
                extension.urlConfig != null -> "url-download-${extension.urlConfig!!.fileName ?: "apk"}"
                else -> "unknown"
            }

            val outputFile = extension.outputFile ?: File(defaultOutputDir, "$defaultOutputName.jar")

            if (extension.outputFile == null) {
                logger.info("Using default output file: ${outputFile.absolutePath}")
            } else {
                logger.info("Using custom output file: ${outputFile.absolutePath}")
            }

            val currentConfig = ConfigSnapshot.from(extension)
            val configChanged = hasConfigurationChanged(currentConfig, outputFile)

            val jarExists = outputFile.exists()

            if (!configChanged && jarExists && !extension.forceReprocess) {
                logger.info("Configuration unchanged and JAR exists, skipping APK download and processing")

                val existingApkFile = findExistingApkFile(outputFile)

                if (existingApkFile != null && existingApkFile.exists()) {
                    processApkTask.configure { task ->
                        task.apkFile.set(existingApkFile)
                        task.outputFile.set(outputFile)
                        task.outputs.upToDateWhen { true }
                        logger.debug("Configured processApk task with existing files")
                    }

                    target.dependencies.add("compileOnly", target.files(outputFile))
                    logger.info("Added existing JAR to compileOnly classpath: ${outputFile.absolutePath}")
                    logger.info("APK Link plugin configuration completed successfully")
                    return@afterEvaluate
                } else {
                    logger.warn("Could not find existing APK file, will redownload")
                }
            }

            if (configChanged) {
                logger.lifecycle("Configuration has changed, will download APK and reprocess")
            }

            val apkFile = try {
                when {
                    extension.apkPureConfig != null -> {
                        val config = extension.apkPureConfig!!
                        val packageName = config.packageName
                            ?: throw RuntimeException("packageName must be specified in apkpure block")

                        val apkPureSource = ApkPureSource(packageName, config.version)
                        val downloadDir = File(target.buildDir, "apkpure-downloads")
                        logger.lifecycle("Downloading APK from APKPure for package $packageName...")
                        apkPureSource.downloadApk(downloadDir)
                    }

                    extension.urlConfig != null -> {
                        val config = extension.urlConfig!!
                        val url = config.url
                            ?: throw RuntimeException("url must be specified in url block")

                        val urlSource = UrlSource(url, config.fileName)
                        val downloadDir = File(target.buildDir, "url-downloads")
                        logger.lifecycle("Downloading APK from URL: $url")
                        urlSource.downloadApk(downloadDir)
                    }

                    extension.apkFile != null -> {
                        logger.info("Using local APK file: ${extension.apkFile!!.absolutePath}")
                        extension.apkFile!!
                    }

                    else -> {
                        logger.warn("No APK source configuration found, skipping ApkLink setup")
                        return@afterEvaluate
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to configure APK source: ${e.message}")
                logger.error("APK Link plugin configuration failed, continuing build without APK dependency")
                return@afterEvaluate
            }

            logger.info("Configured APK file: ${apkFile.absolutePath}")

            saveCurrentConfig(currentConfig, outputFile)

            saveApkLocation(apkFile, outputFile)

            val needsProcessing = configChanged || shouldProcessApk(apkFile, outputFile, extension.forceReprocess)

            if (needsProcessing) {
                logger.lifecycle("Processing APK during configuration phase")
                try {
                    processApk(apkFile, outputFile, logger)
                    logger.lifecycle("APK processed successfully during configuration")
                } catch (e: Exception) {
                    logger.error("Failed to process APK during configuration: ${e.message}")
                    logger.warn("Will attempt processing again during task execution")
                }
            } else {
                logger.info("APK doesn't need reprocessing, using existing JAR")
            }

            processApkTask.configure { task ->
                task.apkFile.set(apkFile)
                task.outputFile.set(outputFile)
                task.outputs.upToDateWhen { !needsProcessing }
                logger.debug("Configured processApk task with input and output files")
            }

            target.dependencies.add("compileOnly", target.files(outputFile))
            logger.info("Added JAR to compileOnly classpath: ${outputFile.absolutePath}")

            logger.info("APK Link plugin configuration completed successfully")
        }
    }

    private fun findExistingApkFile(outputFile: File): File? {
        val apkLocationFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.apklocation")
        if (!apkLocationFile.exists()) {
            return null
        }

        val apkPath = try {
            apkLocationFile.readText().trim()
        } catch (e: Exception) {
            return null
        }

        val apkFile = File(apkPath)
        return if (apkFile.exists()) apkFile else null
    }

    private fun saveApkLocation(apkFile: File, outputFile: File) {
        val apkLocationFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.apklocation")
        try {
            apkLocationFile.parentFile.mkdirs()
            apkLocationFile.writeText(apkFile.absolutePath)
        } catch (e: Exception) {
        }
    }

    private fun shouldProcessApk(apkFile: File, outputFile: File, forceReprocess: Boolean): Boolean {
        if (forceReprocess) {
            return true
        }

        if (!outputFile.exists()) {
            return true
        }

        val hashFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.md5")
        if (!hashFile.exists()) {
            return true
        }

        val storedHash = hashFile.readText().trim()
        val currentHash = calculateMD5(apkFile)

        return storedHash != currentHash
    }

    private fun hasConfigurationChanged(currentConfig: ConfigSnapshot, outputFile: File): Boolean {
        val configFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.config.json")
        if (!configFile.exists()) {
            return true
        }

        try {
            val storedConfigJson = JSONObject(configFile.readText())
            val storedConfig = ConfigSnapshot.fromJson(storedConfigJson)

            return currentConfig != storedConfig
        } catch (e: Exception) {
            return true
        }
    }

    private fun saveCurrentConfig(config: ConfigSnapshot, outputFile: File) {
        val configFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.config.json")
        try {
            configFile.parentFile.mkdirs()
            configFile.writeText(config.toJson().toString(2))
        } catch (_: Exception) {
        }
    }

    private fun processApk(apkFile: File, outputFile: File, logger: org.gradle.api.logging.Logger) {
        if (!apkFile.exists() || !apkFile.canRead()) {
            throw RuntimeException("Cannot read APK file: ${apkFile.absolutePath}")
        }

        outputFile.parentFile.mkdirs()

        logger.lifecycle("Converting APK to JAR using dex2jar...")
        val startTime = System.currentTimeMillis()

        try {
            val reader: BaseDexFileReader = MultiDexFileReader.open(Files.readAllBytes(apkFile.toPath()))
            val handler = BaksmaliBaseDexExceptionHandler()

            Dex2jar.from(reader)
                .withExceptionHandler(handler)
                .reUseReg(false)
                .topoLogicalSort()
                .skipDebug(true)
                .optimizeSynchronized(false)
                .printIR(false)
                .noCode(false)
                .skipExceptions(true)
                .to(outputFile.toPath())

            val duration = System.currentTimeMillis() - startTime
            logger.lifecycle("APK conversion completed successfully in ${duration}ms")

            if (!outputFile.exists()) {
                throw RuntimeException("Failed to create output JAR file")
            }

            val hashFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}.md5")
            val md5 = calculateMD5(apkFile)
            hashFile.writeText(md5)
            logger.debug("Saved APK hash ($md5) to ${hashFile.absolutePath}")

        } catch (e: Exception) {
            logger.error("Failed to convert APK to JAR", e)
            throw RuntimeException("APK conversion failed: ${e.message}", e)
        }
    }
}
