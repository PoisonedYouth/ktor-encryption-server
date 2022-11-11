package com.poisonedyouth.application

import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.domain.defaultSecurityFileSettings
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.security.EncryptionManager
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import org.apache.commons.lang3.RandomStringUtils
import java.io.OutputStream
import java.util.*


interface FileEncryptionService {
    suspend fun encryptFiles(multipart: MultiPartData): List<Pair<String, UploadFile>>
    suspend fun decryptFile(downloadFileDto: DownloadFileDto, outputStream: OutputStream): String
}

class FileEncryptionServiceImpl(
    private val uploadFileRepository: UploadFileRepository
) : FileEncryptionService {
    override suspend fun encryptFiles(multipart: MultiPartData): List<Pair<String, UploadFile>> {
        val resultList = mutableListOf<Pair<String, UploadFile>>()
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val name = part.originalFileName ?: RandomStringUtils.randomAlphabetic(32)
                val encryptedName = UUID.randomUUID().toString()
                val path = ApplicationConfiguration.getUploadDirectory().resolve(encryptedName)
                part.streamProvider().use { inputStream ->

                    val encryptionResult = EncryptionManager.encryptSteam(inputStream, path, name)
                    resultList.add(
                        Pair(
                            encryptionResult.first,
                            UploadFile(
                                filename = name,
                                encryptedFilename = encryptedName,
                                encryptionResult = encryptionResult.second,
                                mimeType = part.contentType?.toString() ?: "*",
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

    override suspend fun decryptFile(downloadFileDto: DownloadFileDto, outputStream: OutputStream): String {
        val uploadFile = uploadFileRepository.findBy(downloadFileDto.filename)
        return if (uploadFile != null) {
            EncryptionManager.decryptStream(
                downloadFileDto.password,
                uploadFile.encryptionResult,
                uploadFile.settings,
                ApplicationConfiguration.getUploadDirectory().resolve(uploadFile.encryptedFilename),
                outputStream
            )
            uploadFile.mimeType
        } else {
            error("Could not find file '${downloadFileDto.filename}")
        }
    }
}