package com.poisonedyouth.persistence

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.api.UploadAction.UPLOAD
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.UploadFileHistory
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.security.EncryptionManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject
import java.nio.file.Files

internal class UploadFileHistoryRepositoryTest : KoinTest {
    private val userRepository by inject<UserRepository>()
    private val uploadFileRepository by inject<UploadFileRepository>()
    private val uploadFileHistoryRepository by inject<UploadFileHistoryRepository>()
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
    fun `save persists new upload history file`() {
        // given
        val uploadFile = createUploadFile("encryptedName")
        val uploadFileHistory = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFile,
            action = UPLOAD
        )

        // when
        val actual = uploadFileHistoryRepository.save(uploadFileHistory)

        // then
        assertThat(actual).isNotNull
        transaction {
            UploadFileHistoryEntity.findByUploadFile(uploadFile.encryptedFilename).run {
                assertThat(this).hasSize(1)
                assertThat(this.first().ipAddress).isEqualTo(uploadFileHistory.ipAddress)
                assertThat(this.first().action).isEqualTo(uploadFileHistory.action.name)
                assertThat(this.first().uploadFile.encryptedFilename)
                    .isEqualTo(uploadFileHistory.uploadFile.encryptedFilename)
                assertThat(this.first().created).isNotNull
            }
        }
    }

    @Test
    fun `save persists throws PersistenceException if upload file does not exist`() {
        // given
        val tempFile = Files.createFile(ktorServerExtension.getTempDirectory().resolve("test.txt"))
        val owner = persistUser("poisonedyouth")


        val uploadFile = UploadFile(
            filename = "filename",
            encryptedFilename = "encryptedfilename",
            encryptionResult = EncryptionManager.encryptSteam(
                "FileContent".byteInputStream(),
                tempFile,
                "secret.txt"
            ).second,
            settings = SecuritySettings(
                fileIntegrityCheckHashingAlgorithm = "SHA-512",
                passwordKeySizeBytes = 32,
                nonceLengthBytes = 64,
                saltLengthBytes = 128,
                iterationCount = 10000,
                gcmParameterSpecLength = 128
            ),
            owner = owner,
            mimeType = "text/plain",
        )
        val uploadFileHistory = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFile,
            action = UPLOAD
        )

        // when + then
        assertThatThrownBy {
            uploadFileHistoryRepository.save(uploadFileHistory)
        }.isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun `save persists throws PersistenceException if persist fails`() {
        // given
        val uploadFile = createUploadFile("encryptedName")
        val uploadFileHistory = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFile,
            action = UPLOAD
        )
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findByEncryptedFilename(any())
        } throws IllegalStateException("Failed")


        // when + then
        assertThatThrownBy {
            uploadFileHistoryRepository.save(uploadFileHistory)
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UploadFileEntity)
    }


    private fun createUploadFile(encryptedName: String): UploadFile {
        val tempFile = Files.createFile(ktorServerExtension.getTempDirectory().resolve(encryptedName))

        val owner = persistUser("poisonedyouth")
        val uploadFile = UploadFile(
            filename = "secret.txt",
            encryptedFilename = encryptedName,
            encryptionResult = EncryptionManager.encryptSteam(
                "FileContent".byteInputStream(),
                tempFile,
                "secret.txt"
            ).second,
            owner = owner,
            mimeType = "text/plain",
            settings = SecuritySettings(
                fileIntegrityCheckHashingAlgorithm = "SHA-512",
                passwordKeySizeBytes = 64,
                nonceLengthBytes = 32,
                saltLengthBytes = 128,
                iterationCount = 10000,
                gcmParameterSpecLength = 128
            )
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

    @Test
    fun `findAllBy returns matching upload history`() {
        // given
        val uploadFile = createUploadFile("encryptedName1")
        val uploadFileHistory = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFile,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistory)

        val uploadFileOther = createUploadFile("encryptedName2")
        val uploadFileHistoryOther = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFileOther,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistoryOther)

        // when
        val actual = uploadFileHistoryRepository.findAllBy(uploadFile.owner!!)


        // then
        assertThat(actual).hasSize(2)
    }

    @Test
    fun `findAllBy returns empty list for no matching upload history`() {
        // given
        val uploadFile = createUploadFile("encryptedName1")
        val uploadFileHistory = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFile,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistory)

        val uploadFileOther = createUploadFile("encryptedName2")
        val uploadFileHistoryOther = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFileOther,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistoryOther)

        // when
        val actual = uploadFileHistoryRepository.findAllBy(persistUser("otherUser"))


        // then
        assertThat(actual).hasSize(0)
    }

    @Test
    fun `findAllBy throws PersistenceException if user does not exist`() {
        // given
        val uploadFile = createUploadFile("encryptedName1")
        val uploadFileHistory = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFile,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistory)

        val uploadFileOther = createUploadFile("encryptedName2")
        val uploadFileHistoryOther = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFileOther,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistoryOther)

        // when + then
        assertThatThrownBy {
            uploadFileHistoryRepository.findAllBy(
                User(
                    username = "otherUser",
                    encryptionResult = EncryptionManager.encryptPassword("password"),
                    securitySettings = SecuritySettings(
                        fileIntegrityCheckHashingAlgorithm = "SHA-512",
                        passwordKeySizeBytes = 64,
                        nonceLengthBytes = 32,
                        saltLengthBytes = 128,
                        iterationCount = 1200,
                        gcmParameterSpecLength = 128
                    )
                )
            )
        }.isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun `findAllBy throws PersistenceException if loading fails`() {
        // given
        mockkObject(UploadFileHistoryEntity)
        every {
            UploadFileHistoryEntity.findAllByUploadFiles(any())
        } throws IllegalStateException("Failed")

        val uploadFile = createUploadFile("encryptedName1")
        val uploadFileHistory = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFile,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistory)

        val uploadFileOther = createUploadFile("encryptedName2")
        val uploadFileHistoryOther = UploadFileHistory(
            ipAddress = "10.0.0.1",
            uploadFile = uploadFileOther,
            action = UPLOAD
        )
        uploadFileHistoryRepository.save(uploadFileHistoryOther)

        // when + then
        assertThatThrownBy {
            uploadFileHistoryRepository.findAllBy(uploadFile.owner!!)
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UploadFileHistoryEntity)
    }

}