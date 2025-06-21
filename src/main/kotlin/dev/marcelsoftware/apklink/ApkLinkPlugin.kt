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
import java.io.File
import java.nio.file.Files

open class ApkLinkConfig {
    var apkFile: File? = null
    var outputFile: File? = null
    var apkPureConfig: ApkPureConfig? = null
    var urlConfig: UrlConfig? = null
    var enabled: Boolean = true

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

        } catch (e: Exception) {
            logger.error("Failed to convert APK to JAR", e)
            logger.error("Error details: ${e.message}")
            throw RuntimeException("APK conversion failed: ${e.message}", e)
        }

        logger.lifecycle("APK processing task completed successfully")
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

            val apkFile = try {
                when {
                    extension.apkPureConfig != null -> {
                        val config = extension.apkPureConfig!!
                        val packageName = config.packageName
                            ?: throw RuntimeException("packageName must be specified in apkpure block")

                        val apkPureSource = ApkPureSource(packageName, config.version)
                        val downloadDir = File(target.buildDir, "apkpure-downloads")
                        apkPureSource.downloadApk(downloadDir)
                    }

                    extension.urlConfig != null -> {
                        val config = extension.urlConfig!!
                        val url = config.url
                            ?: throw RuntimeException("url must be specified in url block")

                        val urlSource = UrlSource(url, config.fileName)
                        val downloadDir = File(target.buildDir, "url-downloads")
                        urlSource.downloadApk(downloadDir)
                    }

                    extension.apkFile != null -> {
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

            val outputFile = extension.outputFile
                ?: File(target.buildDir, "apklink/${apkFile.nameWithoutExtension}.jar")

            if (extension.outputFile == null) {
                logger.info("Using default output file: ${outputFile.absolutePath}")
            } else {
                logger.info("Using custom output file: ${outputFile.absolutePath}")
            }

            processApkTask.configure { task ->
                task.apkFile.set(apkFile)
                task.outputFile.set(outputFile)
                logger.debug("Configured processApk task with input and output files")
            }

            target.dependencies.add("compileOnly", target.files(outputFile))

            logger.info("APK Link plugin configuration completed successfully")
        }
    }
}
