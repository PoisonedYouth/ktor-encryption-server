package com.poisonedyouth.persistence

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.security.EncryptionManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.test.KoinTest
import org.koin.test.inject
import java.io.File

@ExtendWith(KtorServerExtension::class)
internal class UploadFileRepositoryTest : KoinTest {
    private val userRepository by inject<UserRepository>()
    private val uploadFileRepository by inject<UploadFileRepository>()

    @BeforeEach
    fun clearDatabase() {
        transaction {
            UploadFileEntity.all().forEach { it.delete() }
            UserEntity.all().forEach { it.delete() }
        }
    }

    @Test
    fun `save persists new upload file`() {
        // given
        val tempFile = File.createTempFile("test", "txt")

        val owner = persistUser()
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
            )
        )

        // when
        val actual = uploadFileRepository.save(uploadFile)

        // then
        assertThat(actual).isNotNull
        transaction {
            val uploadFileEntity = UploadFileEntity.findByEncryptedFilename(actual.encryptedFilename)!!
            uploadFileEntity.run {
                assertThat(this.iv).isEqualTo(uploadFile.encryptionResult.initializationVector)
                assertThat(this.nonce).isEqualTo(uploadFile.encryptionResult.nonce)
                assertThat(this.salt).isEqualTo(uploadFile.encryptionResult.salt)
                assertThat(this.hashSum).isEqualTo(uploadFile.encryptionResult.hashSum)
                assertThat(this.encryptedFilename).isEqualTo(uploadFile.encryptedFilename)
                assertThat(this.filename).isEqualTo(uploadFile.filename)
                assertThat(this.user.username).isEqualTo(owner.username)
                assertThat(this.settings.fileIntegrityCheckHashingAlgorithm)
                    .isEqualTo(uploadFile.settings.fileIntegrityCheckHashingAlgorithm)
                assertThat(this.settings.passwordKeySizeBytes)
                    .isEqualTo(uploadFile.settings.passwordKeySizeBytes)
                assertThat(this.settings.nonceLengthBytes).isEqualTo(uploadFile.settings.nonceLengthBytes)
                assertThat(this.settings.saltLengthBytes)
                    .isEqualTo(uploadFile.settings.saltLengthBytes)
                assertThat(this.settings.iterationCount)
                    .isEqualTo(uploadFile.settings.iterationCount)
                assertThat(this.settings.gcmParameterSpecLength)
                    .isEqualTo(uploadFile.settings.gcmParameterSpecLength)
                assertThat(this.created).isNotNull
            }
        }

        tempFile.deleteOnExit()
    }

    @Test
    fun `save throws PersistenceException if upload file already exist`() {
        // given
        val tempFile = File.createTempFile("test", "txt")

        val owner = persistUser()
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
            )
        )
        val actual = uploadFileRepository.save(uploadFile)

        // when + then
        assertThatThrownBy {
            uploadFileRepository.save(actual)
        }.isInstanceOf(PersistenceException::class.java)

        tempFile.deleteOnExit()
    }

    @Test
    fun `save throws PersistenceException if not able to persist`() {
        // given
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findByEncryptedFilename(any())
        } throws IllegalArgumentException("Failed!")

        val tempFile = File.createTempFile("test", "txt")

        val owner = persistUser()
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
            )
        )

        // when + then
        assertThatThrownBy {
            uploadFileRepository.save(uploadFile)
        }.isInstanceOf(PersistenceException::class.java)

        tempFile.deleteOnExit()

        unmockkObject(UploadFileEntity)
    }

    private fun persistUser(): User {
        val user = User(
            username = "poisonedyouth",
            encryptionResult = EncryptionManager.encryptPassword("password"),
            userSettings = UserSettings(uploadFileExpirationDays = 12),
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