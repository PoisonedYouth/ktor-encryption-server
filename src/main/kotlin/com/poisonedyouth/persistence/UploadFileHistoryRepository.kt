package com.poisonedyouth.persistence

import com.poisonedyouth.domain.UploadAction
import com.poisonedyouth.domain.UploadFileHistory
import com.poisonedyouth.domain.User
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

interface UploadFileHistoryRepository {

    fun save(uploadFileHistory: UploadFileHistory): UploadFileHistory
    fun findAllBy(existingUser: User): List<UploadFileHistory>
}

class UploadFileHistoryRepositoryImpl : UploadFileHistoryRepository {
    private val logger: Logger = LoggerFactory.getLogger(UploadFileRepository::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun save(uploadFileHistory: UploadFileHistory): UploadFileHistory = transaction {
        try {
            val currentDateTime = LocalDateTime.now().truncatedTo(SECONDS)
            UploadFileHistoryEntity.new {
                ipAddress = uploadFileHistory.ipAddress
                created = currentDateTime
                action = uploadFileHistory.action.name
                uploadFile =
                    UploadFileEntity.findByEncryptedFilename(uploadFileHistory.uploadFile.encryptedFilename)
                        ?: error("Upload file not found.")
            }
            uploadFileHistory.copy(
                created = currentDateTime,
            )
        } catch (e: Exception) {
            logger.error("Failed to save upload file history '$uploadFileHistory' to database.", e)
            throw PersistenceException("Failed to save upload file history '$uploadFileHistory' to database.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findAllBy(existingUser: User): List<UploadFileHistory> = transaction {
        try {
            val userEntity = UserEntity.getUserOrThrow(existingUser.username)
            val uploadFiles = UploadFileEntity.findAllByUsername(userEntity.id.value)
            UploadFileHistoryEntity.findAllByUploadFiles(uploadFiles.map { it.id.value }).map {
                it.toUploadFileHistory()
            }
        } catch (e: Exception) {
            logger.error("Failed to find upload file history for user '$existingUser' from database.", e)
            throw PersistenceException("Failed to find upload file history for user '$existingUser' from database.")
        }
    }
}

fun UploadFileHistoryEntity.toUploadFileHistory() = UploadFileHistory(
    uploadFile = this.uploadFile.toUploadFile(),
    ipAddress = this.ipAddress,
    created = this.created,
    action = UploadAction.valueOf(this.action)
)