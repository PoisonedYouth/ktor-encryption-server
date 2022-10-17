package com.poisonedyouth.persistence

import com.poisonedyouth.application.UPLOAD_DIRECTORY
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.security.FileEncryptionResult
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

interface UploadFileRepository {
    fun save(uploadFile: UploadFile): UploadFile
    fun findBy(encryptedFilename: String): UploadFile?
    fun findAllByUsername(username: String): List<UploadFile>
    fun deleteExpiredFiles(): List<String>
    fun deleteBy(username: String, encryptedFilename: String): Boolean
    fun deleteAllBy(username: String): List<String>
}

class UploadFileRepositoryImpl : UploadFileRepository {
    private val logger: Logger = LoggerFactory.getLogger(UploadFileRepository::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun save(uploadFile: UploadFile): UploadFile = transaction {
        try {
            if (UploadFileEntity.findByEncryptedFilename(uploadFile.encryptedFilename) != null) {
                error("Upload file with encrypted filename '${uploadFile.encryptedFilename}' already exist.")
            }
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
                settings = SecuritySettingsEntity.newFromSecuritySettings(uploadFile.settings)
            }

            uploadFile.copy(
                created = currentDateTime,
            )
        } catch (e: Exception) {
            logger.error("Failed to save upload file '$uploadFile' to database.", e)
            throw PersistenceException("Failed to save upload file '$uploadFile' to database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findBy(encryptedFilename: String): UploadFile? = transaction {
        try {
            UploadFileEntity.find { UploadFileTable.encryptedFilename eq encryptedFilename }.firstOrNull()
                ?.toUploadFile()
        } catch (e: Exception) {
            logger.error("Failed to find upload file with name '$encryptedFilename' in database.", e)
            throw PersistenceException("Failed to find upload file with name '$encryptedFilename' in database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun deleteExpiredFiles(): List<String> = transaction {
        try {
            val result = UploadFileEntity.all().filter {
                it.created.plusDays(it.user.userSettings.uploadFileExpirationDays) < LocalDateTime.now()
            }
            val fileNames = result.map { it.encryptedFilename }
            result.forEach {
                it.delete()
                File("$UPLOAD_DIRECTORY/${it.encryptedFilename}").delete()
            }
            fileNames
        } catch (e: Exception) {
            logger.error("Failed to delete expired files from database.", e)
            throw PersistenceException("Failed to delete expired files from database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findAllByUsername(username: String): List<UploadFile> = transaction {
        try {
            val userEntity = UserEntity.findUserOrThrow(username)
            UploadFileEntity.find { UploadFileTable.user eq userEntity.id }.map { it.toUploadFile() }
        } catch (e: Exception) {
            logger.error("Failed to find all upload files by username '$username' in database.", e)
            throw PersistenceException("Failed to find all upload files by username '$username' in database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun deleteBy(username: String, encryptedFilename: String): Boolean = transaction {
        try {
            val userEntity = UserEntity.findUserOrThrow(username)
            val uploadFile = findUploadFile(encryptedFilename, userEntity)
            if (uploadFile != null) {
                uploadFile.delete()
                File("$UPLOAD_DIRECTORY/${encryptedFilename}").delete()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete upload file with name '$encryptedFilename' from database.", e)
            throw PersistenceException("Failed to delete upload file with name '$encryptedFilename' from database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun deleteAllBy(username: String): List<String> = transaction {
        try {
            val userEntity = UserEntity.findUserOrThrow(username)
            val result = UploadFileEntity.find { UploadFileTable.user eq userEntity.id }
            val fileNames = result.map { it.encryptedFilename }
            result.forEach {
                it.delete()
                File("$UPLOAD_DIRECTORY/${it.encryptedFilename}").delete()
            }
            fileNames
        } catch (e: Exception) {
            logger.error("Failed to delete all upload files of user with username '$username' from database.", e)
            throw PersistenceException(
                "Failed to delete all upload files of user with username '$username' from database.",
                e
            )
        }
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
    settings = this.settings.toSecuritySettings(),
    created = this.created
)

fun SecuritySettingsEntity.toSecuritySettings() = SecuritySettings(
    fileIntegrityCheckHashingAlgorithm = this.fileIntegrityCheckHashingAlgorithm,
    passwordKeySizeBytes = this.passwordKeySizeBytes,
    nonceLengthBytes = this.nonceLengthBytes,
    saltLengthBytes = this.saltLengthBytes,
    iterationCount = this.iterationCount,
    gcmParameterSpecLength = this.gcmParameterSpecLength
)