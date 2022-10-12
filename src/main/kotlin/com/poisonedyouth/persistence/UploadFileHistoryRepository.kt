package com.poisonedyouth.persistence

import com.poisonedyouth.domain.UploadAction
import com.poisonedyouth.domain.UploadFileHistory
import com.poisonedyouth.domain.User
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

interface UploadFileHistoryRepository {

    fun save(uploadFileHistory: UploadFileHistory): UploadFileHistory
    fun findAllBy(existingUser: User): List<UploadFileHistory>
}

class UploadFileHistoryRepositoryImpl : UploadFileHistoryRepository {

    override fun save(uploadFileHistory: UploadFileHistory): UploadFileHistory = transaction {
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
    }

    override fun findAllBy(existingUser: User): List<UploadFileHistory> = transaction {
        val userEntity = UserEntity.findUserOrThrow(existingUser.username)
        val uploadFiles = UploadFileEntity.find { UploadFileTable.user eq userEntity.id }
        UploadFileHistoryEntity.find { UploadFileHistoryTable.uploadFile inList uploadFiles.map { it.id } }.map {
            it.toUploadFileHistory()
        }
    }
}

fun UploadFileHistoryEntity.toUploadFileHistory() = UploadFileHistory(
    uploadFile = this.uploadFile.toUploadFile(),
    ipAddress = this.ipAddress,
    created = this.created,
    action = UploadAction.valueOf(this.action)
)