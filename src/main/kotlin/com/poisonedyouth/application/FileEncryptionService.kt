package com.poisonedyouth.application

import com.poisonedyouth.api.DownloadFileDto
import com.poisonedyouth.domain.UploadFile
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.security.EncryptionManager
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import org.apache.commons.lang3.RandomStringUtils
import java.io.File
import java.util.*

interface FileEncryptionService {
    suspend fun encryptFiles(multipart: MultiPartData): List<Pair<String, UploadFile>>
    suspend fun decryptFile(downloadFileDto: DownloadFileDto): File
}

private const val UPLOAD_DIRECTORY = "./uploads"
private const val DOWNLOAD_DIRECTORY = "./downloads"

class FileEncryptionServiceImpl(
    private val uploadFileRepository: UploadFileRepository
) : FileEncryptionService {

    override suspend fun encryptFiles(multipart: MultiPartData): List<Pair<String, UploadFile>> {
        val resultList = mutableListOf<Pair<String, UploadFile>>()
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val name = part.originalFileName ?: RandomStringUtils.randomAlphabetic(16)
                val encryptedName = UUID.randomUUID().toString()
                val file =
                    File("$UPLOAD_DIRECTORY/$encryptedName")

                part.streamProvider().use { its ->
                    val encryptionResult = EncryptionManager.encryptSteam(its, file)
                    resultList.add(
                        Pair(
                            encryptionResult.first,
                            UploadFile(
                                filename = name,
                                encryptedFilename = encryptedName,
                                encryptionResult = encryptionResult.second
                            )
                        )
                    )
                }
                part.dispose()
            }
        }
        return resultList
    }

    override suspend fun decryptFile(downloadFileDto: DownloadFileDto): File {
        val uploadFile = uploadFileRepository.findBy(downloadFileDto.filename)
        if (uploadFile != null) {
            val outputFile = File("$DOWNLOAD_DIRECTORY/${uploadFile.filename}")
            return try {
                EncryptionManager.decryptStream(
                    downloadFileDto.password,
                    uploadFile.encryptionResult,
                    File("$UPLOAD_DIRECTORY/${uploadFile.encryptedFilename}"),
                    outputFile
                )
            } catch (e: Exception) {
                outputFile.delete()
                throw e
            }
        } else {
            error("Could not find file '${downloadFileDto.filename}")
        }
    }
}