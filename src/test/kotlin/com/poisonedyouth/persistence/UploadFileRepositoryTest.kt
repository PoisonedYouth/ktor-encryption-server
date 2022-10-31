package com.poisonedyouth.persistence

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.security.EncryptionManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject
import java.nio.file.Files
import java.time.LocalDateTime

internal class UploadFileRepositoryTest : KoinTest {
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
            UploadFileEntity.all().forEach { it.delete() }
            UserEntity.all().forEach { it.delete() }
        }

    }

    @Test
    fun `save persists new upload file`() {
        // given
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
    }

    @Test
    fun `save throws PersistenceException if upload file already exist`() {
        // given
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
            )
        )
        val actual = uploadFileRepository.save(uploadFile)

        // when + then
        assertThatThrownBy {
            uploadFileRepository.save(actual)
        }.isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun `save throws PersistenceException if not able to persist`() {
        // given
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findByEncryptedFilename(any())
        } throws IllegalArgumentException("Failed!")

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
            )
        )

        // when + then
        assertThatThrownBy {
            uploadFileRepository.save(uploadFile)
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UploadFileEntity)
    }

    @Test
    fun `findBy returns matching upload file`() {
        // given
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
            )
        )
        val persistedUploadFile = uploadFileRepository.save(uploadFile)

        // when
        val actual = uploadFileRepository.findBy(persistedUploadFile.encryptedFilename)

        // then
        assertThat(actual).isNotNull
    }

    @Test
    fun `findBy returns null for no matching upload file`() {
        // given
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
            )
        )
        uploadFileRepository.save(uploadFile)

        // when
        val actual = uploadFileRepository.findBy("no matching file")

        // then
        assertThat(actual).isNull()
    }

    @Test
    fun `findBy throws PersistenceException if loading of upload file fails`() {
        // given
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
            )
        )
        uploadFileRepository.save(uploadFile)

        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findByEncryptedFilename(any())
        } throws IllegalArgumentException("Failed!")

        // when
        assertThatThrownBy {
            uploadFileRepository.findBy("no matching file")
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UploadFileEntity)
    }

    @Test
    fun `findAllBy returns matching upload files`() {
        // given
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
            )
        )
        uploadFileRepository.save(uploadFile)
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)

        // when
        val actual = uploadFileRepository.findAllBy(owner.username)

        // then
        assertThat(actual).hasSize(2)
    }

    @Test
    fun `findAllBy returns empty list for no matching files`() {
        // given
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
            )
        )
        uploadFileRepository.save(uploadFile)
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)

        // when
        val actual = uploadFileRepository.findAllBy(persistUser("otherUSer").username)

        // then
        assertThat(actual).isEmpty()
    }

    @Test
    fun `findAllBy throws PeristenceException if loading of upload files fails`() {
        // given
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findAllByUsername(any())
        } throws IllegalArgumentException("Failed!")

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
            )
        )
        uploadFileRepository.save(uploadFile)
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)

        // when
        assertThatThrownBy {
            uploadFileRepository.findAllBy("otherUSer")
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UploadFileEntity)
    }

    @Test
    fun `deleteExpiredFiles removes matching files`() {
        // given
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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // make files expired
        transaction { UploadFileEntity.all().forEach { it.created = LocalDateTime.now().minusDays(12) } }


        // when
        uploadFileRepository.deleteExpiredFiles()

        // then
        assertThat(transaction { UploadFileEntity.all().count() }).isZero
        assertThat(ktorServerExtension.getTempDirectory().listDirectoryEntries().map { it.fileName.name }).doesNotContain(
            uploadFile.encryptedFilename,
            otherUploadFile.encryptedFilename
        )
    }

    @Test
    fun `deleteExpiredFiles not removes not expired files`() {
        // given
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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when
        uploadFileRepository.deleteExpiredFiles()

        // then
        assertThat(transaction { UploadFileEntity.all().count() }).isEqualTo(2)
        assertThat(ktorServerExtension.getTempDirectory().listDirectoryEntries().map { it.fileName.name }).contains(
            uploadFile.encryptedFilename,
            otherUploadFile.encryptedFilename
        )
    }

    @Test
    fun `deleteExpiredFiles throws PersistenceException if deletion of upload files fails`() {
        // given
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.getAll()
        } throws IllegalArgumentException("Failed")

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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when + then
        assertThatThrownBy {
            uploadFileRepository.deleteExpiredFiles()
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UploadFileEntity)
    }

    @Test
    fun `deleteBy throws PersistenceException if user does not exist`() {
        // given
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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when + then
        assertThatThrownBy {
            uploadFileRepository.deleteBy("not existing user", otherUploadFile.encryptedFilename)
        }.isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun `deleteBy returns false if upload file does not exist`() {
        // given
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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when
        val actual = uploadFileRepository.deleteBy(owner.username, "not existing file")

        assertThat(actual).isFalse
        assertThat(transaction { UploadFileEntity.getAll().count() }).isEqualTo(2)
    }

    @Test
    fun `deleteBy throws PersistenceException if deletion fails`() {
        // given
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findByEncryptedFilenameAndUser(any(), any())
        } throws IllegalStateException("Failed")

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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when
        assertThatThrownBy {
            uploadFileRepository.deleteBy(owner.username, uploadFile.encryptedFilename)
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UploadFileEntity)
    }

    @Test
    fun `deleteBy removes matching upload file`() {
        // given
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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when
        val actual = uploadFileRepository.deleteBy(owner.username, uploadFile.encryptedFilename)

        // then
        assertThat(actual).isTrue
        assertThat(transaction { UploadFileEntity.findByEncryptedFilename(uploadFile.encryptedFilename) }).isNull()
        assertThat(
            ApplicationConfiguration.getUploadDirectory().listDirectoryEntries()
                .map { it.fileName.name }).doesNotContain(uploadFile.encryptedFilename)
    }

    @Test
    fun `deleteAllBy removes matching upload files`() {
        // given
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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when
        val actual = uploadFileRepository.deleteAllBy(owner.username)

        // then
        assertThat(actual).hasSize(2)
        assertThat(transaction { UploadFileEntity.all().count() }).isZero
        assertThat(
            ApplicationConfiguration.getUploadDirectory().listDirectoryEntries().map { it.fileName.name }
        ).doesNotContain(uploadFile.encryptedFilename, otherUploadFile.encryptedFilename)
    }

    @Test
    fun `deleteAllBy throws PersistenceException if user does not exist`() {
        // given
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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when
        assertThatThrownBy {
            uploadFileRepository.deleteAllBy("not existing user")
        }.isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun `deleteAllBy throws PersistenceException if deletion fails`() {
        // given
        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findAllByUsername(any())
        } throws IllegalStateException("Failed")

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
        uploadFileRepository.save(uploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(uploadFile.encryptedFilename), "text1")
        val otherUploadFile = UploadFile(
            filename = "otherFile.txt",
            encryptedFilename = "encrypted2",
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
        uploadFileRepository.save(otherUploadFile)
        Files.writeString(ktorServerExtension.getTempDirectory().resolve(otherUploadFile.encryptedFilename), "text2")

        // when
        assertThatThrownBy {
            uploadFileRepository.deleteAllBy(owner.username)
        }.isInstanceOf(PersistenceException::class.java)

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