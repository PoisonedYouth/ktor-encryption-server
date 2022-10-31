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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject
import java.nio.file.Files

internal class CleanupTaskTest : KoinTest {
    private val uploadFileRepository by inject<UploadFileRepository>()
    private val cleanupTask by inject<CleanupTask>()
    private val userRepository by inject<UserRepository>()
    companion object {
        @RegisterExtension
        @JvmStatic
        private val ktorServerExtension = KtorServerExtension()
    }

    @BeforeEach
    fun clearDatabase() {
        transaction {
            UploadFileEntity.all().forEach { it.delete() }
        }

    }

    @Test
    fun `run deletes orphaned files`() {
        // given
        Files.createFile(ApplicationConfiguration.getUploadDirectory().resolve("file1.txt"))
        Files.createFile(ApplicationConfiguration.getUploadDirectory().resolve("file2.txt"))

        // when
        cleanupTask.run()


        // then
        assertThat(ApplicationConfiguration.getUploadDirectory().listDirectoryEntries()).isEmpty()
    }

    @Test
    fun `run not deletes active files`() {
        // given
        val tempFile = Files.createFile(ktorServerExtension.getTempDirectory().resolve("test.txt"))

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
        cleanupTask.run()


        // then
        assertThat(ApplicationConfiguration.getUploadDirectory().listDirectoryEntries()).contains(tempFile)
    }

    @Test
    fun `run not throws exception if loading files fails`() {
        // given
        val tempFile = Files.createFile(ktorServerExtension.getTempDirectory().resolve("test.txt"))

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

        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findByEncryptedFilename(any())
        } throws PersistenceException("Failed")


        // when
        assertThatNoException().isThrownBy {
            cleanupTask.run()
        }

        // then
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