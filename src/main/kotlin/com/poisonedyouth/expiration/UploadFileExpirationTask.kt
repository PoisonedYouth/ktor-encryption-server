package com.poisonedyouth.expiration

import com.poisonedyouth.persistence.PersistenceException
import com.poisonedyouth.persistence.UploadFileRepository
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit


class UploadFileExpirationTask(
    private val uploadFileRepository: UploadFileRepository
) : TimerTask() {
    private val logger: Logger = LoggerFactory.getLogger(UploadFileExpirationTask::class.java)

    override fun run() {
        logger.info("Start detecting expired upload files...")
        try {
            val result = uploadFileRepository.deleteExpiredFiles()
            result.forEach { logger.info("Deleted upload file '${it}'") }
            if (result.isEmpty()) {
                logger.info("No upload files to delete.")
            }
        } catch (e: PersistenceException) {
            logger.error("Failed to delete expired files. Will be skipped...", e)
        }
        logger.info("Finish detecting expired upload files.")
    }

    fun scheduleUploadFileExpiration() {
        Timer("UploadFileExpirationTask").schedule(
            this,
            TimeUnit.MINUTES.toMillis(1),
            TimeUnit.DAYS.toMillis(1)
        )
    }

}

fun Application.setupUploadFileExpirationTask() {
    val uploadFileExpirationTask by inject<UploadFileExpirationTask>()

    uploadFileExpirationTask.scheduleUploadFileExpiration()

}