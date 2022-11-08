package com.poisonedyouth.application

import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.application.ErrorCode.NOT_ACCEPTED_MIME_TYPE
import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.defaultSecurityFileSettings
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.security.EncryptionException
import com.poisonedyouth.security.EncryptionManager
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.RandomStringUtils
import org.apache.tika.config.TikaConfig
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*


interface FileEncryptionService {
    suspend fun encryptFiles(multipart: MultiPartData): List<Pair<String, UploadFile>>
    suspend fun decryptFile(downloadFileDto: DownloadFileDto): Path
}

class FileEncryptionServiceImpl(
    private val uploadFileRepository: UploadFileRepository
) : FileEncryptionService {
    override suspend fun encryptFiles(multipart: MultiPartData): List<Pair<String, UploadFile>> {
        val resultList = mutableListOf<Pair<String, UploadFile>>()
        multipart.forEachPart { part ->
            val tika = TikaConfig()
            if (part is PartData.FileItem) {
                val name = part.originalFileName ?: RandomStringUtils.randomAlphabetic(32)
                val encryptedName = UUID.randomUUID().toString()
                val path = ApplicationConfiguration.getUploadDirectory().resolve(encryptedName)
                part.streamProvider().use { inputStream ->
                    validateMimeType(tika, inputStream, name)

                    val encryptionResult = EncryptionManager.encryptSteam(inputStream, path)
                    resultList.add(
                        Pair(
                            encryptionResult.first,
                            UploadFile(
                                filename = name,
                                encryptedFilename = encryptedName,
                                encryptionResult = encryptionResult.second,
                                settings = defaultSecurityFileSettings()
                            )
                        )
                    )
                }
                part.dispose()
            }
        }
        return resultList
    }

    private fun validateMimeType(tika: TikaConfig, inputStream: InputStream, name: String?) {
        val metadata = Metadata()
        val mimetype = tika.detector.detect(TikaInputStream.get(inputStream), metadata)
        val validMimeTypes = ApplicationConfiguration.uploadSettings.validMimeTypes
        if (!validMimeTypes.contains(mimetype.baseType.toString())) {
            throw ApplicationServiceException(errorCode = NOT_ACCEPTED_MIME_TYPE, "Given mimetype of upload '$name' (${mimetype.type}) is not one of ($validMimeTypes).")
        }
    }

    override suspend fun decryptFile(downloadFileDto: DownloadFileDto): Path {
        val uploadFile = uploadFileRepository.findBy(downloadFileDto.filename)
        if (uploadFile != null) {
            val outputFile = withContext(Dispatchers.IO) {
                Files.createTempDirectory(UUID.randomUUID().toString())
            }.resolve(uploadFile.filename)
            return try {
                EncryptionManager.decryptStream(
                    downloadFileDto.password,
                    uploadFile.encryptionResult,
                    uploadFile.settings,
                    ApplicationConfiguration.getUploadDirectory().resolve(uploadFile.encryptedFilename),
                    outputFile
                )
            } catch (e: EncryptionException) {
                withContext(Dispatchers.IO) {
                    deleteDirectoryRecursively(outputFile.parent)
                }
                throw e
            }
        } else {
            error("Could not find file '${downloadFileDto.filename}")
        }
    }
}