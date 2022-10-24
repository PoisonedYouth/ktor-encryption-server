@file:OptIn(ExperimentalCoroutinesApi::class)

package com.poisonedyouth.application

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.api.UpdatePasswordDto
import com.poisonedyouth.api.UploadFileHistoryDto
import com.poisonedyouth.api.UploadFileOverviewDto
import com.poisonedyouth.api.UserDto
import com.poisonedyouth.api.UserSettingsDto
import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.createHttpClient
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
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.InternalAPI
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.test.KoinTest
import org.koin.test.inject
import java.nio.file.Files
import java.util.*

@ExtendWith(KtorServerExtension::class)
class ApiTest : KoinTest {
    private val uploadFileRepository by inject<UploadFileRepository>()
    private val userRepository by inject<UserRepository>()
    private val userService by inject<UserService>()

    @BeforeEach
    fun clearDatabase() {
        transaction {
            UploadFileHistoryEntity.all().forEach { it.delete() }
            UploadFileEntity.all().forEach { it.delete() }
            UserEntity.all().forEach { it.delete() }
        }
    }

    @Test
    fun `create new user is possible`() = runTest {
        // given
        val client = createHttpClient()

        // when
        val response = client.post("http://localhost:8080/api/user") {
            setBody(
                UserDto(
                    username = "poisonedyouth",
                    password = "!1Wertzueu482835",
                    userSettings = UserSettingsDto(
                        uploadFileExpirationDays = 12
                    )
                )
            )
            contentType(ContentType.Application.Json)
        }

        // then
        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val result = response.body<String>()
        assertThat(result).isNotNull
        assertThat(transaction { UserEntity.getUserOrThrow("poisonedyouth") }).isNotNull
    }

    @Test
    fun `delete user is possible`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // when
        val response = client.delete("http://localhost:8080/api/user") {
            contentType(ContentType.Application.Json)
        }

        // then
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val result = response.body<String>()
        assertThat(result).isNotNull
        assertThat(transaction { UserEntity.findUserOrNull("poisonedyouth") }).isNull()
    }

    @Test
    fun `update user password is possible`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // when
        val response = client.put("http://localhost:8080/api/user/password") {
            setBody(
                UpdatePasswordDto(
                    "123456789aB!rtzu"
                )
            )
            contentType(ContentType.Application.Json)
        }

        // then
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val result = response.body<String>()
        assertThat(result).isNotNull
        assertThatNoException().isThrownBy {
            userService.authenticate(
                UserDto(
                    username = user.username,
                    password = "123456789aB!rtzu",
                )
            )
        }
    }

    @Test
    fun `update user settings is possible`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // when
        val response = client.put("http://localhost:8080/api/user/settings") {
            setBody(
                UserSettingsDto(
                    51
                )
            )
            contentType(ContentType.Application.Json)
        }

        // then
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val result = response.body<String>()
        assertThat(result).isNotNull
        assertThat(transaction
                   {
                       UserEntity.findUserOrNull("poisonedyouth")!!.userSettings.uploadFileExpirationDays
                   }).isEqualTo(51)
    }

    @Test
    fun `upload file is working`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // when
        val uploadFile = Files.createTempFile("file1.txt", "")
        Files.writeString(uploadFile, "Hello World!")
        val response = client.submitFormWithBinaryData(
            url = "http://localhost:8080/api/upload",
            formData = formData {
                append("file", uploadFile.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "text/plain")
                    append(HttpHeaders.ContentDisposition, "filename=file1.txt")
                })
            }
        )

        // then
        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        assertThat(ApplicationConfiguration.getUploadDirectory().listDirectoryEntries()).hasSize(1)
    }

    @Test
    fun `get uploads is working`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        createUploadResult(user)

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // when
        val response = client.get("http://localhost:8080/api/upload") {
            contentType(ContentType.Application.Json)
        }

        // then
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val result = response.body<ApiResult.Success<List<UploadFileOverviewDto>>>()
        assertThat(result.value).hasSize(1)
        assertThat(result.value.first().encryptedFilename).isEqualTo("encrypted")
    }

    @Test
    fun `get upload history is working`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // Upload file
        val uploadFile = Files.createTempFile("file1.txt", "")
        Files.writeString(uploadFile, "Hello World!")
        client.submitFormWithBinaryData(
            url = "http://localhost:8080/api/upload",
            formData = formData {
                append("file", uploadFile.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "text/plain")
                    append(HttpHeaders.ContentDisposition, "filename=file1.txt")
                })
            }
        )

        // when
        val response = client.get("http://localhost:8080/api/upload/history") {
            contentType(ContentType.Application.Json)
        }

        // then
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val result = response.body<ApiResult.Success<List<UploadFileHistoryDto>>>()
        assertThat(result.value).hasSize(1)
        assertThat(result.value.first().action).isEqualTo(UploadAction.UPLOAD)
    }

    @OptIn(InternalAPI::class)
    @Test
    fun `download file is working`() = runTest {
        // given
        val user = persistUser("poisonedyouth", "Ab1!999999999999")

        val client = createHttpClient(username = user.username, password = "Ab1!999999999999")

        // Upload file
        val uploadFileResult = createUploadResult(user)

        // when
        val response = client.get("http://localhost:8080/api/download") {
            parameter("password", uploadFileResult.first)
            parameter("encryptedfilename", uploadFileResult.second.encryptedFilename)
        }

        // then
        val downloadFile = withContext(Dispatchers.IO) {
            Files.createTempFile("download", "txt")
        }
        response.content.copyAndClose(downloadFile.toFile().writeChannel())
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(withContext(Dispatchers.IO) {
            Files.readString(downloadFile)
        }).isEqualTo("FileContent")

        downloadFile.deleteIfExists()
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

    private fun createUploadResult(user: User): Pair<String, UploadFile> {
        val tempFile = Files.createFile(KtorServerExtension.basePath.resolve("encrypted"))

        val encryptionResult = EncryptionManager.encryptSteam(
            "FileContent".byteInputStream(),
            tempFile
        )
        val uploadFile = UploadFile(
            filename = "secret.txt",
            encryptedFilename = "encrypted",
            encryptionResult = encryptionResult.second,
            owner = user,
            settings = SecuritySettings(
                fileIntegrityCheckHashingAlgorithm = "SHA-512",
                passwordKeySizeBytes = 256,
                nonceLengthBytes = 32,
                saltLengthBytes = 64,
                iterationCount = 10000,
                gcmParameterSpecLength = 128
            ),
        )
        return Pair(encryptionResult.first, uploadFileRepository.save(uploadFile))
    }
}