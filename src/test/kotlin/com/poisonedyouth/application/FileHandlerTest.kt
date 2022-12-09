package com.poisonedyouth.application

import com.poisonedyouth.KtorServerExtension
import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.application.ApiResult.Failure
import com.poisonedyouth.application.ApiResult.Success
import com.poisonedyouth.domain.SecuritySettings
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.persistence.UploadFileEntity
import com.poisonedyouth.persistence.UploadFileHistoryEntity
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UploadFileTable
import com.poisonedyouth.persistence.UserEntity
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.security.EncryptionManager
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.RequestConnectionPoint
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.headersOf
import io.ktor.util.encodeBase64
import io.ktor.utils.io.streams.asInput
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import javax.imageio.ImageIO
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.test.KoinTest
import org.koin.test.inject
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
internal class FileHandlerTest : KoinTest {
    private val fileHandler by inject<FileHandler>()
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
    fun `getUploadFiles returns all matching upload files`() = runTest {
        // given
        val uploadFile = createUploadResult().second

        // when
        val actual = fileHandler.getUploadFiles(uploadFile.owner!!.username)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        (actual as Success).value.run {
            assertThat(this).hasSize(1)
            assertThat(this[0].encryptedFilename).isEqualTo(uploadFile.encryptedFilename)
            assertThat(this[0].filename).isEqualTo(uploadFile.filename)
            assertThat(this[0].hashSumBase64).isEqualTo(uploadFile.encryptionResult.hashSum.encodeBase64())
        }
    }

    @Test
    fun `getUploadFiles returns failure if loading fails`() = runTest {
        // given
        val uploadFile = createUploadResult().second

        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findAllByUsername(any())
        } throws IllegalStateException("Failed")

        // when
        val actual = fileHandler.getUploadFiles(uploadFile.owner!!.username)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.PERSISTENCE_FAILURE)

        unmockkObject(UploadFileEntity)
    }

    @Test
    fun `upload returns list of single uploaded file`() = runTest {
        // given
        val user = persistUser("poisonedyouth")


        val multiPartData = createMultipartData(listOf("file1.txt"))

        // when
        val actual = fileHandler.upload(
            username = user.username,
            origin = createRequestConnectionPoint(),
            contentLength = 10,
            multiPartData = multiPartData
        )

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).hasSize(1)
        assertThat(actual.value.first().filename).isEqualTo("file1.txt")
        assertThat(actual.value.first().downloadLink).isNotBlank()
        assertThat(actual.value.first().deleteLink).isNotBlank()
    }

    @Test
    fun `upload returns failure if uploaded files exceed limit`() = runTest {
        // given
        val user = persistUser("poisonedyouth")


        val multiPartData = createMultipartData(listOf("file1.txt"))

        // when
        val actual = fileHandler.upload(
            username = user.username,
            origin = createRequestConnectionPoint(),
            contentLength = 500000000,
            multiPartData = multiPartData
        )

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.UPLOAD_SIZE_LIMIT_EXCEEDED)
    }

    @Test
    fun `upload returns failure if uploaded file is not a valid mime type`() = runTest {
        // given
        val user = persistUser("poisonedyouth")

        // Create not allowed mime type
        val rgb = BufferedImage(100, 100, TYPE_INT_RGB)
        val file = withContext(Dispatchers.IO) {
            val file = Files.createFile(ktorServerExtension.getTempDirectory().resolve("file1.png"))
            ImageIO.write(rgb, "png", file.outputStream())
            file
        }
        val multiPartData = createMultipartData(listOf("file1.txt"), file.inputStream())

        // when
        val actual = fileHandler.upload(
            username = user.username,
            origin = createRequestConnectionPoint(),
            contentLength = 5000,
            multiPartData = multiPartData
        )

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.NOT_ACCEPTED_MIME_TYPE)
    }

    @Test
    fun `upload returns failure if content length header is missing`() = runTest {
        // given
        val user = persistUser("poisonedyouth")


        val multiPartData = createMultipartData(listOf("file1.txt"))

        // when
        val actual = fileHandler.upload(
            username = user.username,
            origin = createRequestConnectionPoint(),
            contentLength = null,
            multiPartData = multiPartData
        )

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.MISSING_HEADER)
    }

    @Test
    fun `upload returns list of multiple uploaded file`() = runTest {
        // given
        val user = persistUser("poisonedyouth")


        val multiPartData = createMultipartData(listOf("file1.txt", "file2.txt"))

        // when
        val actual = fileHandler.upload(
            username = user.username,
            origin = createRequestConnectionPoint(),
            contentLength = 10,
            multiPartData = multiPartData
        )

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).hasSize(2)
        assertThat(actual.value.first().filename).isEqualTo("file1.txt")
        assertThat(actual.value.get(1).filename).isEqualTo("file2.txt")
    }

    @Test
    fun `upload returns failure if user does not exist`() = runTest {
        // given
        persistUser("poisonedyouth")


        val multiPartData = createMultipartData(listOf("file1.txt"))

        // when
        val actual = fileHandler.upload(
            username = "not existing user",
            origin = createRequestConnectionPoint(),
            contentLength = 10,
            multiPartData = multiPartData
        )

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.USER_NOT_FOUND)
    }

    @Test
    fun `upload returns failure if encryption fails`() = runTest {
        // given
        val user = persistUser("poisonedyouth")

        val multiPartData = createMultipartData(listOf("file1.txt"))

        mockkObject(UploadFileEntity)
        every {
            UploadFileEntity.findByEncryptedFilename(any())
        } throws IllegalStateException("Failed")

        // when
        val actual = fileHandler.upload(
            username = user.username,
            origin = createRequestConnectionPoint(),
            contentLength = 10,
            multiPartData = multiPartData
        )

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.PERSISTENCE_FAILURE)

        unmockkObject(UploadFileEntity)
    }

    @Test
    fun `download returns path of download file`() = runTest {
        // given
        val uploadResult = createUploadResult()

        val downloadFileDto = DownloadFileDto(
            password = uploadResult.first,
            filename = uploadResult.second.encryptedFilename
        )

        // when
        val outputStream = ByteArrayOutputStream()
        val actual = fileHandler.download(downloadFileDto, "10.1.1.1", outputStream)

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((outputStream.toString(StandardCharsets.UTF_8))).isEqualTo("FileContent")
    }

    @Test
    fun `download returns failure if file does not exist`() = runTest {
        // given
        val uploadResult = createUploadResult()

        val downloadFileDto = DownloadFileDto(
            password = uploadResult.first,
            filename = "not existing file"
        )

        // when
        val actual = fileHandler.download(downloadFileDto, "10.1.1.1", ByteArrayOutputStream())

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.FILE_NOT_FOUND)
    }

    @Test
    fun `download returns failure if decryption of file fails`() = runTest {
        // given
        val uploadResult = createUploadResult()

        val encryptedFilename = uploadResult.second.encryptedFilename
        val downloadFileDto = DownloadFileDto(
            password = uploadResult.first,
            filename = encryptedFilename
        )

        // Corrupt hashsum to make download fail
        transaction {
            UploadFileEntity
                .find { UploadFileTable.encryptedFilename eq encryptedFilename }.first().hashSum = ByteArray(8)
        }

        // when
        val actual = fileHandler.download(downloadFileDto, "10.1.1.1", ByteArrayOutputStream())

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.ENCRYPTION_FAILURE)
    }

    @Test
    fun `delete returns failure if encryptedFilename is null`() = runTest {
        // given
        val user = persistUser("poisonedyouth")

        // when
        val actual = fileHandler.delete(user.username, null)

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.MISSING_PARAMETER)
    }

    @Test
    fun `delete returns failure if user does not exist`() = runTest {
        // given
        persistUser("poisonedyouth")

        // when
        val actual = fileHandler.delete("not existing user", "encrypted")

        // then
        assertThat(actual).isInstanceOf(Failure::class.java)
        assertThat((actual as Failure).errorCode).isEqualTo(ErrorCode.PERSISTENCE_FAILURE)
    }

    @Test
    fun `delete returns success for existing file`() = runTest {
        // given
        val uploadResult = createUploadResult()

        // when
        val actual = fileHandler.delete(uploadResult.second.owner!!.username, "encrypted")

        // then
        assertThat(actual).isInstanceOf(Success::class.java)
        assertThat((actual as Success).value).isTrue()
    }


    private fun createRequestConnectionPoint(): RequestConnectionPoint {
        return object : RequestConnectionPoint {
            override val localHost: String
                get() = "10.1.1.1"
            override val method: HttpMethod
                get() = TODO("Not yet implemented")
            override val localPort: Int
                get() = 8080
            override val remoteHost: String
                get() = "10.1.1.1"
            override val scheme: String
                get() = TODO("Not yet implemented")
            override val uri: String
                get() = TODO("Not yet implemented")
            override val version: String
                get() = TODO("Not yet implemented")
            override val host: String
                get() = "10.1.1.1"
            override val localAddress: String
                get() = TODO("Not yet implemented")
            override val port: Int
                get() = 8080
            override val remoteAddress: String
                get() = TODO("Not yet implemented")
            override val remotePort: Int
                get() = TODO("Not yet implemented")
            override val serverHost: String
                get() = TODO("Not yet implemented")
            override val serverPort: Int
                get() = TODO("Not yet implemented")
        }
    }

    private fun createMultipartData(filenames: List<String>, inputStream: InputStream = byteArrayOf(1, 2, 3).inputStream()): MultiPartData {
        val fileItems = filenames.map {
            PartData.FileItem({ inputStream.asInput() }, {}, headersOf(
                HttpHeaders.ContentDisposition,
                ContentDisposition.File
                    .withParameter(ContentDisposition.Parameters.Name, "file")
                    .withParameter(ContentDisposition.Parameters.FileName, it)
                    .toString()
            )
            )
        }

        return object : MultiPartData {
            var index = 0
            override suspend fun readPart(): PartData? {
                val fileItem = fileItems.getOrNull(index)
                index += 1
                return fileItem
            }

        }
    }

    private fun createUploadResult(): Pair<String, UploadFile> {
        val tempFile = Files.createFile(ktorServerExtension.getTempDirectory().resolve("encrypted"))

        val owner = persistUser("poisonedyouth")
        val encryptionResult = EncryptionManager.encryptSteam(
            "FileContent".byteInputStream(),
            tempFile,
            "secret.txt"
        )
        val uploadFile = UploadFile(
            filename = "secret.txt",
            encryptedFilename = "encrypted",
            encryptionResult = encryptionResult.second,
            owner = owner,
            mimeType = "text/plain",
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

    private fun persistUser(username: String): User {
        val user = User(
            username = username,
            encryptionResult = EncryptionManager.encryptPassword("Ab1!999999999999"),
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