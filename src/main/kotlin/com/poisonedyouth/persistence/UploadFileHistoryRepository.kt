package com.poisonedyouth.persistence

import com.poisonedyouth.domain.UploadFileHistory
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

interface UploadFileHistoryRepository {

    fun save(uploadFileHistory: UploadFileHistory): UploadFileHistory
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
}