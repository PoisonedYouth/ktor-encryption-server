package com.poisonedyouth.expiration

import com.poisonedyouth.application.UPLOAD_DIRECTORY
import com.poisonedyouth.persistence.PersistenceException
import com.poisonedyouth.persistence.UploadFileRepository
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


class CleanupTask(
    private val uploadFileRepository: UploadFileRepository
) : TimerTask() {
    private val logger: Logger = LoggerFactory.getLogger(CleanupTask::class.java)

    override fun run() {
        logger.info("Start cleanup files...")
        File(UPLOAD_DIRECTORY).listFiles()?.forEach {
            try {
                val result = uploadFileRepository.findBy(it.name)
                if (result == null) {
                    logger.info("Delete orphaned upload file with encrypted filename '${it.name}'")
                    it.delete()
                }
            } catch (e: PersistenceException){
                logger.error("Failed to find file with name '$it.name'. Will be skipped...")
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