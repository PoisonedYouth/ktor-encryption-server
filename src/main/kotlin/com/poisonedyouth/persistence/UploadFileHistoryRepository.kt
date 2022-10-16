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
                    UploadFileEntity.find { UploadFileTable.encryptedFilename eq uploadFileHistory.uploadFile.encryptedFilename }
                        .first()
            }
            uploadFileHistory.copy(
                created = currentDateTime,
            )
        } catch (e: Exception) {
            logger.error("Failed to save upload file history '$uploadFileHistory' to database.", e)
            throw PersistenceException("Failed to save upload file history '$uploadFileHistory' to database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findAllBy(user: User): List<UploadFileHistory> = transaction {
        try {
            val userEntity = UserEntity.findUserOrThrow(user.username)
            val uploadFiles = UploadFileEntity.find { UploadFileTable.user eq userEntity.id }
            UploadFileHistoryEntity.find { UploadFileHistoryTable.uploadFile inList uploadFiles.map { it.id } }.map {
                it.toUploadFileHistory()
            }
        } catch (e: Exception) {
            logger.error("Failed to find upload file history for user '$user' from database.", e)
            throw PersistenceException("Failed to find upload file history for user '$user' from database.", e)
        }
    }
}

fun UploadFileHistoryEntity.toUploadFileHistory() = UploadFileHistory(
    uploadFile = this.uploadFile.toUploadFile(),
    ipAddress = this.ipAddress,
    created = this.created,
    action = UploadAction.valueOf(this.action)
)