package com.poisonedyouth.application

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.KtorServerExtension.Companion
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.UploadAction
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.persistence.UploadFileEntity
import com.poisonedyouth.persistence.UploadFileHistoryEntity
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserEntity
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.test.KoinTest
import org.koin.test.inject
import java.nio.file.Files
import java.time.LocalDateTime

@ExtendWith(KtorServerExtension::class)
internal class UploadFileHistoryServiceTest : KoinTest {
    private val uploadFileHistoryService by inject<UploadFileHistoryService>()
    private val userRepository by inject<UserRepository>()
    private val uploadFileRepository by inject<UploadFileRepository>()

    @BeforeEach
    fun clearDatabase() {
        transaction {
            UploadFileHistoryEntity.all().forEach { it.delete() }
            UploadFileEntity.all().forEach { it.delete() }
            UserEntity.all().forEach { it.delete() }
        }
    }

    @Test
    fun `addUploadFileHistoryEntry returns failure if upload file does not exist`() {
        // given
        val ipAddress = "10.1.1.1"
        val action = UploadAction.UPLOAD

        // when
        val actual = uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            encryptedFilename = "encryptedFilename"
        )

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.FILE_NOT_FOUND)
    }

    @Test
    fun `addUploadFileHistoryEntry returns success`() {
        // given
        val uploadFile = createUploadFile()

        val ipAddress = "10.1.1.1"
        val action = UploadAction.UPLOAD

        // when
        val actual = uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            encryptedFilename = uploadFile.encryptedFilename
        )

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
    }

    private fun createUploadFile(): UploadFile {
        val tempFile = Files.createFile(KtorServerExtension.basePath.resolve("test.txt"))

        val owner = persistUser("poisonedyouth")
        val uploadFile = UploadFile(
            filename = "secret.txt",
            encryptedFilename = "encrypted",
            encryptionResult = EncryptionManager.encryptSteam(
                "FileContent".byteInputStream(),
                tempFile
            ).second,
            owner = owner,
            settings = SecuritySettings(
                fileIntegrityCheckHashingAlgorithm = "SHA-512",
                passwordKeySizeBytes = 64,
                nonceLengthBytes = 32,
                saltLengthBytes = 128,
                iterationCount = 10000,
                gcmParameterSpecLength = 128
            ),
            created = LocalDateTime.now().minusDays(10)
        )
        return uploadFileRepository.save(uploadFile)
    }

    private fun persistUser(username: String): User {
        val user = User(
            username = username,
            encryptionResult = EncryptionManager.encryptPassword("password"),
            userSettings = UserSettings(uploadFileExpirationDays = 2),
            securitySettings = SecuritySettings(
                fileIntegrityCheckHashingAlgorithm = "SHA-512",
                passwordKeySizeBytes = 64,
                nonceLengthBytes = 32,
                saltLengthBytes = 128,
                iterationCount = 1200,
                gcmParameterSpecLength = 128
            )
        )
        return userRepository.save(user)
    }
}