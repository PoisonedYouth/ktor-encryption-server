package com.poisonedyouth.application

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.api.UpdatePasswordDto
import com.poisonedyouth.api.UserDto
import com.poisonedyouth.api.UserSettingsDto
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.application.ErrorCode.ENCRYPTION_FAILURE
import com.poisonedyouth.application.ErrorCode.INPUT_VALIDATION_FAILED
import com.poisonedyouth.application.ErrorCode.PASSWORD_REQUIREMENTS_NOT_FULFILLED
import com.poisonedyouth.application.ErrorCode.PERSISTENCE_FAILURE
import com.poisonedyouth.application.ErrorCode.USER_ALREADY_EXIST
import com.poisonedyouth.application.ErrorCode.USER_NOT_FOUND
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.persistence.UploadFileEntity
import com.poisonedyouth.persistence.UploadFileHistoryEntity
import com.poisonedyouth.persistence.UserEntity
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionException
import com.poisonedyouth.security.EncryptionManager
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.test.KoinTest
import org.koin.test.inject

@ExtendWith(KtorServerExtension::class)
internal class UserServiceTest : KoinTest {
    private val userService by inject<UserService>()
    private val userRepository by inject<UserRepository>()

    @BeforeEach
    fun clearDatabase() {
        transaction {
            UploadFileHistoryEntity.all().forEach { it.delete() }
            UploadFileEntity.all().forEach { it.delete() }
            UserEntity.all().forEach { it.delete() }
        }

    }

    @Test
    fun `authenticate returns failure if user is not available`() {
        // given
        val userDto = UserDto(
            username = "not existing user",
            password = "password"
        )

        // when
        val actual = userService.authenticate(userDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(USER_NOT_FOUND)
    }

    @Test
    fun `authenticate returns failure if decrypt password fails`() {
        // given
        val user = persistUser("poisonedyouth")

        val userDto = UserDto(
            username = user.username,
            password = "other password"
        )

        // when
        val actual = userService.authenticate(userDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ENCRYPTION_FAILURE)
    }

    @Test
    fun `authenticate returns success if password is correct`() {
        // given
        val user = persistUser("poisonedyouth")

        val userDto = UserDto(
            username = user.username,
            password = "Ab1!999999999999"
        )

        // when
        val actual = userService.authenticate(userDto)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
    }

    @Test
    fun `save returns failure if user already exist`() {
        // given
        val user = persistUser("poisonedyouth")

        val userDto = UserDto(
            username = user.username,
            password = "password"
        )

        // when
        val actual = userService.save(userDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(USER_ALREADY_EXIST)
    }

    @Test
    fun `save returns failure if password does not fulfill requirements`() {
        // given
        val userDto = UserDto(
            username = "poisonedyouth",
            password = "password"
        )

        // when
        val actual = userService.save(userDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(PASSWORD_REQUIREMENTS_NOT_FULFILLED)
    }

    @Test
    fun `save returns success if is persisted successfully`() {
        // given
        val userDto = UserDto(
            username = "poisonedyouth",
            password = "Ab1!999999999999"
        )

        // when
        val actual = userService.save(userDto)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).isEqualTo(userDto.username)
    }

    @Test
    fun `delete returns failure if user does not exist`() {
        // given
        val username = "poisonedyouth"

        // when
        val actual = userService.delete(username)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(USER_NOT_FOUND)
    }

    @Test
    fun `delete returns failure if delete fails`() {
        // given
        mockkObject(UserEntity)
        every {
            UserEntity.getUserOrThrow(any())
        } throws IllegalStateException("Failed")

        val user = persistUser("poisonedyouth")

        // when
        val actual = userService.delete(user.username)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(PERSISTENCE_FAILURE)

        unmockkObject(UserEntity)
    }

    @Test
    fun `delete returns success if delete is successful`() {
        // given
        val user = persistUser("poisonedyouth")

        // when
        val actual = userService.delete(user.username)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).isEqualTo("Successfully deleted user")
    }

    @Test
    fun `updatePassword returns failure if user does not exist`() {
        // given
        persistUser("poisonedyouth")

        val updatePasswordDto = UpdatePasswordDto(
            newPassword = "new password"
        )

        // when
        val actual = userService.updatePassword("not existing user", updatePasswordDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(USER_NOT_FOUND)
    }

    @Test
    fun `updatePassword returns failure if new password does not fulfill requirements`() {
        // given
        val user = persistUser("poisonedyouth")

        val updatePasswordDto = UpdatePasswordDto(
            newPassword = "new password"
        )

        // when
        val actual = userService.updatePassword(user.username, updatePasswordDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(PASSWORD_REQUIREMENTS_NOT_FULFILLED)
    }

    @Test
    fun `updatePassword returns failure if encryption fails`() {
        // given
        val user = persistUser("poisonedyouth")

        val updatePasswordDto = UpdatePasswordDto(
            newPassword = "Ccq1!12588888888"
        )

        mockkObject(EncryptionManager)
        every {
            EncryptionManager.encryptPassword(any())
        } throws EncryptionException("Failed")

        // when
        val actual = userService.updatePassword(user.username, updatePasswordDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ENCRYPTION_FAILURE)

        unmockkObject(EncryptionManager)
    }

    @Test
    fun `updatePassword returns success if update successful`() {
        // given
        val user = persistUser("poisonedyouth")

        val updatePasswordDto = UpdatePasswordDto(
            newPassword = "Ccq1!12588888888"
        )

        // when
        val actual = userService.updatePassword(user.username, updatePasswordDto)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).isEqualTo("Successfully updated password.")
    }

    @Test
    fun `updateSettings returns success if update successful`() {
        // given
        val user = persistUser("poisonedyouth")

        val userSettingsDto = UserSettingsDto(
            uploadFileExpirationDays = 122
        )

        // when
        val actual = userService.updateSettings(user.username, userSettingsDto)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).isEqualTo("Successfully updated user settings.")
    }

    @Test
    fun `updateSettings returns failure if user does not exist`() {
        // given
        persistUser("poisonedyouth")

        val userSettingsDto = UserSettingsDto(
            uploadFileExpirationDays = 122
        )

        // when
        val actual = userService.updateSettings("not existing user", userSettingsDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(USER_NOT_FOUND)
    }

    @Test
    fun `updateSettings returns failure setting does not fulfill requirements`() {
        // given
        val user = persistUser("poisonedyouth")

        val userSettingsDto = UserSettingsDto(
            uploadFileExpirationDays = -1
        )

        // when
        val actual = userService.updateSettings(user.username, userSettingsDto)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(INPUT_VALIDATION_FAILED)
    }

    private fun persistUser(username: String): User {
        val user = User(
            username = username,
            encryptionResult = EncryptionManager.encryptPassword("Ab1!999999999999"),
            userSettings = UserSettings(uploadFileExpirationDays = 2),
            securitySettings = SecuritySettings(
                fileIntegrityCheckHashingAlgorithm = "SHA-512",
                passwordKeySizeBytes = 256,
                nonceLengthBytes = 32,
                saltLengthBytes = 64,
                iterationCount = 10000,
                gcmParameterSpecLength = 128
            )
        )
        return userRepository.save(user)
    }
}