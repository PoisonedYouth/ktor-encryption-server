package com.poisonedyouth.expiration

import com.poisonedyouth.application.UserService
import com.poisonedyouth.persistence.UploadFileRepository
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

const val EXPIRATION_DAYS: Long = 6

class UploadFileExpirationTask(
    private val uploadFileRepository: UploadFileRepository
) : TimerTask() {
    private val logger: Logger = LoggerFactory.getLogger(UploadFileExpirationTask::class.java)

    override fun run() {
        logger.info("Start detecting expired upload files...")
        val result = uploadFileRepository.deleteExpiredFiles(EXPIRATION_DAYS, ChronoUnit.DAYS)
        result.forEach { logger.info("Deleted upload file '${it}'") }
        if (result.isEmpty()) {
            logger.info("No upload files to delete.")
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