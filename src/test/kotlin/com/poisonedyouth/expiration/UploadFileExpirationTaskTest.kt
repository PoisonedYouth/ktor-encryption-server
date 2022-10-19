package com.poisonedyouth.expiration

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.persistence.PersistenceException
import com.poisonedyouth.persistence.UploadFileEntity
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
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
internal class UploadFileExpirationTaskTest : KoinTest {
    private val uploadFileRepository by inject<UploadFileRepository>()
    private val userRepository by inject<UserRepository>()
    private val expirationTask by inject<UploadFileExpirationTask>()

    @BeforeEach
    fun clearDatabase() {
        transaction {
            UploadFileEntity.all().forEach { it.delete() }
        }
    }

    @Test
    fun `run deletes expired upload files`() {
        // given
        val tempFile = Files.createFile(KtorServerExtension.basePath.resolve("test.txt"))

        val owner = persistUser("poisonedyouth")
        val uploadFile = UploadFile(
            filename = "secret.txt",
            encryptedFilename = tempFile.fileName.name,
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
        uploadFileRepository.save(uploadFile)

        // make files expired
        transaction { UploadFileEntity.all().forEach { it.created = LocalDateTime.now().minusDays(12) } }

        // when
        expirationTask.run()

        // then
        assertThat(transaction { UploadFileEntity.all().count() }).isEqualTo(0)
        assertThat(ApplicationConfiguration.getUploadDirectory().listDirectoryEntries()).doesNotContain(tempFile)
    }

    @Test
    fun `run not deletes active upload files`() {
        // given
        val tempFile = Files.createFile(KtorServerExtension.basePath.resolve("test.txt"))

        val owner = persistUser("poisonedyouth")
        val uploadFile = UploadFile(
            filename = "secret.txt",
            encryptedFilename = tempFile.fileName.name,
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
        uploadFileRepository.save(uploadFile)

        // when
        expirationTask.run()

        // then
        assertThat(transaction { UploadFileEntity.all().count() }).isEqualTo(1)
        assertThat(ApplicationConfiguration.getUploadDirectory().listDirectoryEntries()).contains(tempFile)
    }

    @Test
    fun `run not throws exception when deleting upload files fails`() {
        // given
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.getAll()
        } throws PersistenceException("Failed")

        val tempFile = Files.createFile(KtorServerExtension.basePath.resolve("test.txt"))

        val owner = persistUser("poisonedyouth")
        val uploadFile = UploadFile(
            filename = "secret.txt",
            encryptedFilename = tempFile.fileName.name,
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
        uploadFileRepository.save(uploadFile)

        // when
        assertThatNoException().isThrownBy {
            expirationTask.run()
        }

        // then
        assertThat(transaction { UploadFileEntity.all().count() }).isEqualTo(1)
        assertThat(ApplicationConfiguration.getUploadDirectory().listDirectoryEntries()).contains(tempFile)

        unmockkObject(UploadFileEntity)
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