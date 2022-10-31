package com.poisonedyouth.application

import com.poisonedyouth.KtorServerExtension
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
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject
import java.nio.file.Files
import java.time.LocalDateTime

internal class UploadFileHistoryServiceTest : KoinTest {
    private val uploadFileHistoryService by inject<UploadFileHistoryService>()
    private val userRepository by inject<UserRepository>()
    private val uploadFileRepository by inject<UploadFileRepository>()
    companion object {
        @RegisterExtension
        @JvmStatic
        private val ktorServerExtension = KtorServerExtension()
    }

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
    fun `addUploadFileHistoryEntry with uploadFile returns failure if file does not exist`() {
        // given
        val ipAddress = "10.1.1.1"
        val action = UploadAction.UPLOAD

        val tempFile = Files.createFile(ktorServerExtension.getTempDirectory().resolve("test.txt"))

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


        // when
        val actual = uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            uploadFile,
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

    @Test
    fun `addUploadFileHistoryEntry using upload file returns success`() {
        // given
        val uploadFile = createUploadFile()

        val ipAddress = "10.1.1.1"
        val action = UploadAction.UPLOAD

        // when
        val actual = uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            uploadFile = uploadFile
        )

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
    }

    @Test
    fun `getUploadFileHistory returns matching upload history`() {
        // given
        val uploadFile = createUploadFile()

        val ipAddress = "10.1.1.1"
        val action = UploadAction.UPLOAD
        uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            encryptedFilename = uploadFile.encryptedFilename
        )

        // when
        val actual = uploadFileHistoryService.getUploadFileHistory(uploadFile.owner!!.username)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).hasSize(1)
        assertThat(actual.value.first().action).isEqualTo(action)
        assertThat(actual.value.first().ipAddress).isEqualTo(ipAddress)
        assertThat(actual.value.first().encryptedFilename).isEqualTo(uploadFile.encryptedFilename)
    }

    @Test
    fun `getUploadFileHistory returns failure if user does not exist`() {
        // given
        val uploadFile = createUploadFile()

        val ipAddress = "10.1.1.1"
        val action = UploadAction.UPLOAD
        uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            encryptedFilename = uploadFile.encryptedFilename
        )

        // when
        val actual = uploadFileHistoryService.getUploadFileHistory("not existing user")

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.USER_NOT_FOUND)
    }

    @Test
    fun `getUploadFileHistory returns failure if loading fails`() {
        // given
        val uploadFile = createUploadFile()

        val ipAddress = "10.1.1.1"
        val action = UploadAction.UPLOAD
        uploadFileHistoryService.addUploadFileHistoryEntry(
            ipAddress = ipAddress,
            action = action,
            encryptedFilename = uploadFile.encryptedFilename
        )

        mockkObject(UploadFileHistoryEntity)
        every {
            UploadFileHistoryEntity.findAllByUploadFiles(any())
        } throws IllegalStateException("Failed")

        // when
        val actual = uploadFileHistoryService.getUploadFileHistory(uploadFile.owner!!.username)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.PERSISTENCE_FAILURE)

        unmockkObject(UploadFileHistoryEntity)
    }

    private fun createUploadFile(): UploadFile {
        val tempFile = Files.createFile(ktorServerExtension.getTempDirectory().resolve("test.txt"))

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