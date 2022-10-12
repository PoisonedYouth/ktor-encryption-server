package com.poisonedyouth.persistence

import com.poisonedyouth.application.UPLOAD_DIRECTORY
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.security.FileEncryptionResult
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS
import java.time.temporal.TemporalUnit

interface UploadFileRepository {
    fun save(uploadFile: UploadFile): UploadFile
    fun findBy(encryptedFilename: String): UploadFile?
    fun findAllByUsername(username: String): List<UploadFile>
    fun deleteExpiredFiles(amount: Long, unit: TemporalUnit): List<String>
    fun deleteBy(username: String, encryptedFilename: String): Boolean
    fun deleteAllBy(username: String): List<String>
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

    override fun deleteExpiredFiles(amount: Long, unit: TemporalUnit): List<String> = transaction {
        val result = UploadFileEntity.find { UploadFileTable.created less LocalDateTime.now().minus(amount, unit) }
        val fileNames = result.map { it.encryptedFilename }
        result.forEach {
            it.delete()
            File("$UPLOAD_DIRECTORY/${it.encryptedFilename}").delete()
        }
        fileNames
    }

    override fun findAllByUsername(username: String): List<UploadFile> = transaction {
        val userEntity = findUserOrThrow(username)
        UploadFileEntity.find { UploadFileTable.user eq userEntity.id }.map { it.toUploadFile() }
    }

    override fun deleteBy(username: String, encryptedFilename: String): Boolean = transaction {
        val userEntity = findUserOrThrow(username)
        val uploadFile = findUploadFile(encryptedFilename, userEntity)
        if (uploadFile != null) {
            uploadFile.delete()
            File("$UPLOAD_DIRECTORY/${encryptedFilename}").delete()
            true
        } else {
            false
        }
    }

    override fun deleteAllBy(username: String): List<String> = transaction {
        val userEntity = findUserOrThrow(username)
        val result = UploadFileEntity.find { UploadFileTable.user eq userEntity.id }
        val fileNames = result.map { it.encryptedFilename }
        result.forEach {
            it.delete()
            File("$UPLOAD_DIRECTORY/${it.encryptedFilename}").delete()
        }
        fileNames
    }

    private fun findUploadFile(
        encryptedFilename: String,
        userEntity: UserEntity
    ): UploadFileEntity? {
        val uploadFile =
            UploadFileEntity.find {
                (UploadFileTable.encryptedFilename eq encryptedFilename)
                    .and(UploadFileTable.user eq userEntity.id)
            }
                .firstOrNull()
        return uploadFile
    }

    private fun findUserOrThrow(username: String): UserEntity {
        return UserEntity.find { UserTable.username eq username }.firstOrNull()
            ?: error("No user available for username '$username'.")
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