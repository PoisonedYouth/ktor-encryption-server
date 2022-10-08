package com.poisonedyouth.persistence

import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.security.FileEncryptionResult
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

interface UploadFileRepository {
    fun save(uploadFile: UploadFile): UploadFile

    fun findBy(encryptedFilename: String): UploadFile?
}

class UploadFileRepositoryImpl : UploadFileRepository {
    override fun save(uploadFile: UploadFile): UploadFile = transaction {
        val currentDateTime = LocalDateTime.now().truncatedTo(SECONDS)
        UploadFileEntity.new {
            filename = uploadFile.filename
            encryptedFilename = uploadFile.encryptedFilename
            hashSum = uploadFile.encryptionResult.hashSum
            iv = uploadFile.encryptionResult.initializationVector
            salt = uploadFile.encryptionResult.salt
            nonce = uploadFile.encryptionResult.nonce
            created = currentDateTime
            user = UserEntity.find { UserTable.username eq uploadFile.owner!!.username }.first()
        }

        uploadFile.copy(
            created = currentDateTime,
        )
    }

    override fun findBy(encryptedFilename: String): UploadFile? = transaction {
        UploadFileEntity.find { UploadFileTable.encryptedFilename eq encryptedFilename }.firstOrNull()?.toUploadFile()
    }
}

fun UploadFileEntity.toUploadFile() = UploadFile(
    filename = this.filename,
    encryptedFilename = this.encryptedFilename,
    encryptionResult = FileEncryptionResult(
        initializationVector = this.iv,
        hashSum = this.hashSum,
        nonce = this.nonce,
        salt = this.salt
    ),
    owner = this.user.toUser(),
    created = this.created
)