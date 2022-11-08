package com.poisonedyouth.api

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.createHttpClient
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.persistence.UploadFileEntity
import com.poisonedyouth.persistence.UploadFileHistoryEntity
import com.poisonedyouth.persistence.UserEntity
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject

internal class UserControllerTest : KoinTest {
    private val userRepository by inject<UserRepository>()

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
    fun `create new user fails if body is of wrong type`() = runTest {
        // given
        val client = createHttpClient()

        // when
        val response = client.post("http://localhost:8080/api/user") {
            setBody(
                UserSettingsDto(uploadFileExpirationDays = 12)
            )
            contentType(Application.Json)
        }

        // then
        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val result = response.body<String>()
        Assertions.assertThat(result).isEqualTo("Failed to transform body.")
    }

    @Test
    fun `update user password fails if body is of wrong type`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // when
        val response = client.put("http://localhost:8080/api/user/password") {
            setBody(
                UserDto(
                    username = "poisonedyouth",
                    password = "123456789aB!rtzu"
                )
            )
            contentType(Application.Json)
        }

        // then
        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val result = response.body<String>()
        Assertions.assertThat(result).isEqualTo("Failed to transform body.")
    }

    @Test
    fun `update user settings fails if body is of wrong type`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // when
        val response = client.put("http://localhost:8080/api/user/settings") {
            setBody(
                UserDto(
                    username = "poisonedyouth",
                    password = "123456789aB!rtzu"
                )
            )
            contentType(Application.Json)
        }

        // then
        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val result = response.body<String>()
        Assertions.assertThat(result).isEqualTo("Failed to transform body.")
    }

    private fun persistUser(username: String, password: String): User {
        val user = User(
            username = username,
            encryptionResult = EncryptionManager.encryptPassword(password),
            userSettings = UserSettings(uploadFileExpirationDays = 50),
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