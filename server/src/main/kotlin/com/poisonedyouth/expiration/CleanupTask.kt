package com.poisonedyouth.expiration

import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.persistence.PersistenceException
import com.poisonedyouth.persistence.UploadFileRepository
import io.ktor.server.application.Application
import kotlin.io.path.name
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit


class CleanupTask(
    private val uploadFileRepository: UploadFileRepository
) : TimerTask() {
    private val logger: Logger = LoggerFactory.getLogger(CleanupTask::class.java)

    override fun run() {
        logger.info("Start cleanup files...")
        Files.newDirectoryStream(ApplicationConfiguration.getUploadDirectory()).use { directoryStream ->
            directoryStream.forEach {
                try {
                    val result = uploadFileRepository.findBy(it.fileName.name)
                    if (result == null) {
                        logger.info("Delete orphaned upload file with encrypted filename '${it.fileName.name}'")
                        Files.delete(it)
                    }
                } catch (e: PersistenceException) {
                    logger.error("Failed to find file with name '$it.name'. Will be skipped...", e)
                }
            }
        }
        logger.info("Finish cleanup files.")
    }

    fun scheduleCleanup() {
        Timer("UploadFileExpirationTask").schedule(
            this,
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.DAYS.toMillis(1)
        )
    }

}

fun Application.setupCleanupTask() {
    val cleanupTask by inject<CleanupTask>()

    cleanupTask.scheduleCleanup()

}