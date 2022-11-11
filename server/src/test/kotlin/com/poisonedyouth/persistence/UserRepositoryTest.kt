package com.poisonedyouth.persistence

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.domain.SecuritySettings
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
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.test.KoinTest
import org.koin.test.inject

@ExtendWith(KtorServerExtension::class)
internal class UserRepositoryTest : KoinTest {
    private val userRepository by inject<UserRepository>()

    @BeforeEach
    fun clearDatabase() {
        transaction {
            UserEntity.all().forEach { it.delete() }
        }
    }

    @Test
    fun `save persists new user`() {
        // given
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

        // when
        val actual = userRepository.save(user)

        // then
        assertThat(actual).isNotNull
        transaction {
            val userEntity = UserEntity.getUserOrThrow(user.username)
            userEntity.run {
                assertThat(this.username).isEqualTo(user.username)
                assertThat(this.iv).isEqualTo(user.encryptionResult.initializationVector)
                assertThat(this.salt).isEqualTo(user.encryptionResult.salt)
                assertThat(this.nonce).isEqualTo(user.encryptionResult.nonce)
                assertThat(this.hashSum).isEqualTo(user.encryptionResult.hashSum)
                assertThat(this.password).isEqualTo(user.encryptionResult.encryptedPassword)
                assertThat(this.userSettings.uploadFileExpirationDays)
                    .isEqualTo(user.userSettings.uploadFileExpirationDays)
                assertThat(this.securitySettings.fileIntegrityCheckHashingAlgorithm)
                    .isEqualTo(user.securitySettings.fileIntegrityCheckHashingAlgorithm)
                assertThat(this.securitySettings.passwordKeySizeBytes)
                    .isEqualTo(user.securitySettings.passwordKeySizeBytes)
                assertThat(this.securitySettings.nonceLengthBytes).isEqualTo(user.securitySettings.nonceLengthBytes)
                assertThat(this.securitySettings.saltLengthBytes)
                    .isEqualTo(user.securitySettings.saltLengthBytes)
                assertThat(this.securitySettings.iterationCount)
                    .isEqualTo(user.securitySettings.iterationCount)
                assertThat(this.securitySettings.gcmParameterSpecLength)
                    .isEqualTo(user.securitySettings.gcmParameterSpecLength)
                assertThat(this.created).isNotNull
                assertThat(this.lastUpdated).isNotNull
            }
        }
    }

    @Test
    fun `save throws PersistenceException if error occur`() {
        // given
        mockkObject(SecuritySettingsEntity)
        every {
            SecuritySettingsEntity.newFromSecuritySettings(any())
        } throws IllegalArgumentException("Failed!")

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

        // when + then
        assertThatThrownBy {
            userRepository.save(user)
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(SecuritySettingsEntity)
    }

    @Test
    fun `save updates existing user`() {
        // given
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
        userRepository.save(user)

        // when
        val updatedUser = user.copy(
            encryptionResult = EncryptionManager.encryptPassword("otherPassword")
        )
        val actual = userRepository.save(updatedUser)

        // then
        assertThat(actual).isNotNull
        transaction {
            val userEntity = UserEntity.getUserOrThrow(updatedUser.username)
            userEntity.run {
                assertThat(this.username).isEqualTo(updatedUser.username)
                assertThat(this.iv).isEqualTo(updatedUser.encryptionResult.initializationVector)
                assertThat(this.salt).isEqualTo(updatedUser.encryptionResult.salt)
                assertThat(this.nonce).isEqualTo(updatedUser.encryptionResult.nonce)
                assertThat(this.hashSum).isEqualTo(updatedUser.encryptionResult.hashSum)
                assertThat(this.password).isEqualTo(updatedUser.encryptionResult.encryptedPassword)
                assertThat(this.created).isNotNull
                assertThat(this.lastUpdated).isNotNull
            }
        }
    }

    @Test
    fun `delete removes existing user`() {
        // given
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
        userRepository.save(user)

        // when
        userRepository.delete(user.username)

        // then
        assertThat(transaction { UserEntity.all().count() }).isZero
    }

    @Test
    fun `delete fails for non user`() {
        // given
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
        userRepository.save(user)

        // when + then
        assertThatThrownBy {
            userRepository.delete("none existing user")
        }.isInstanceOf(PersistenceException::class.java)
    }

    @Test
    fun `findByUsername returns matching user`() {
        // given
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
        userRepository.save(user)

        // when
        val actual = userRepository.findBy(user.username)

        // then
        assertThat(actual).isNotNull
    }

    @Test
    fun `findByUsername returns null for non matching user`() {
        // given
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
        userRepository.save(user)

        // when
        val actual = userRepository.findBy("non existing user")

        // then
        assertThat(actual).isNull()
    }

    @Test
    fun `findByUsername throws PersistenceException if loading fails`() {
        // given
        mockkObject(UserEntity)
        every {
            UserEntity.findUserOrNull("poisonedyouth")
        } throws IllegalArgumentException("Failed!")

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
        userRepository.save(user)

        // when + then
        assertThatThrownBy {
            userRepository.findBy(user.username)
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UserEntity)
    }

    @Test
    fun `findAll returns matching user`() {
        // given
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
        userRepository.save(user)

        val other = User(
            username = "otherUser",
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
        userRepository.save(other)

        // when
        val actual = userRepository.findAll()

        // then
        assertThat(actual).hasSize(2)
    }

    @Test
    fun `findAll throws PersistenceException if loading fails`() {
        // given
        mockkObject(UserEntity)
        every {
            UserEntity.findAll()
        } throws IllegalStateException("Failed")

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
        userRepository.save(user)

        // when + then
        assertThatThrownBy {
            userRepository.findAll()
        }.isInstanceOf(PersistenceException::class.java)

        unmockkObject(UserEntity)
    }

}