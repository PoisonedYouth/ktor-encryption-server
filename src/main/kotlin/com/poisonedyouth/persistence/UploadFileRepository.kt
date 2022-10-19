package com.poisonedyouth.persistence

import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.security.FileEncryptionResult
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit.SECONDS

interface UploadFileRepository {
    fun save(uploadFile: UploadFile): UploadFile
    fun findBy(encryptedFilename: String): UploadFile?
    fun findAllBy(username: String): List<UploadFile>
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
            throw PersistenceException("Failed to save upload file '$uploadFile' to database.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findBy(encryptedFilename: String): UploadFile? = transaction {
        try {
            UploadFileEntity.findByEncryptedFilename(encryptedFilename)
                ?.toUploadFile()
        } catch (e: Exception) {
            logger.error("Failed to find upload file with name '$encryptedFilename' in database.", e)
            throw PersistenceException("Failed to find upload file with name '$encryptedFilename' in database.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun deleteExpiredFiles(): List<String> = transaction {
        try {
            val result = UploadFileEntity.getAll().filter {
                it.created.plusDays(it.user.userSettings.uploadFileExpirationDays) < LocalDateTime.now()
            }
            val fileNames = result.map { it.encryptedFilename }
            result.forEach {
                it.delete()
                Files.delete(ApplicationConfiguration.getUploadDirectory().resolve(it.encryptedFilename))
            }
            fileNames
        } catch (e: Exception) {
            logger.error("Failed to delete expired files from database.", e)
            throw PersistenceException("Failed to delete expired files from database.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findAllBy(username: String): List<UploadFile> = transaction {
        try {
            val userEntity = UserEntity.getUserOrThrow(username)
            UploadFileEntity.findAllByUsername(userEntity.id.value).map { it.toUploadFile() }
        } catch (e: Exception) {
            logger.error("Failed to find all upload files by username '$username' in database.", e)
            throw PersistenceException("Failed to find all upload files by username '$username' in database.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun deleteBy(username: String, encryptedFilename: String): Boolean = transaction {
        try {
            val userEntity = UserEntity.getUserOrThrow(username)
            val uploadFile = UploadFileEntity.findByEncryptedFilenameAndUser(encryptedFilename, userEntity.id.value)
            if (uploadFile != null) {
                uploadFile.delete()
                Files.delete(ApplicationConfiguration.getUploadDirectory().resolve(encryptedFilename))
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to delete upload file with name '$encryptedFilename' from database.", e)
            throw PersistenceException("Failed to delete upload file with name '$encryptedFilename' from database.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun deleteAllBy(username: String): List<String> = transaction {
        try {
            val userEntity = UserEntity.getUserOrThrow(username)
            val result = UploadFileEntity.findAllByUsername(userEntity.id.value)
            val fileNames = result.map { it.encryptedFilename }
            result.forEach {
                it.delete()
                Files.delete(ApplicationConfiguration.getUploadDirectory().resolve(it.encryptedFilename))
            }
            fileNames
        } catch (e: Exception) {
            logger.error("Failed to delete all upload files of user with username '$username' from database.", e)
            throw PersistenceException(
                "Failed to delete all upload files of user with username '$username' from database."
            )
        }
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