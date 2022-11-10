package com.poisonedyouth.security

import com.poisonedyouth.application.ApplicationServiceException
import com.poisonedyouth.application.ErrorCode.NOT_ACCEPTED_MIME_TYPE
import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.domain.SecuritySettings
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import org.apache.tika.config.TikaConfig
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec


data class FileEncryptionResult(
    val initializationVector: ByteArray,
    val nonce: ByteArray,
    val hashSum: ByteArray,
    val salt: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileEncryptionResult

        if (!initializationVector.contentEquals(other.initializationVector)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!hashSum.contentEquals(other.hashSum)) return false
        if (!salt.contentEquals(other.salt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initializationVector.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + hashSum.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        return result
    }
}

data class PasswordEncryptionResult(
    val initializationVector: ByteArray,
    val nonce: ByteArray,
    val hashSum: ByteArray,
    val encryptedPassword: ByteArray,
    val salt: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasswordEncryptionResult

        if (!initializationVector.contentEquals(other.initializationVector)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!hashSum.contentEquals(other.hashSum)) return false
        if (!encryptedPassword.contentEquals(other.encryptedPassword)) return false
        if (!salt.contentEquals(other.salt)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initializationVector.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + hashSum.contentHashCode()
        result = 31 * result + encryptedPassword.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        return result
    }
}

object EncryptionManager {
    private val logger: Logger = LoggerFactory.getLogger(EncryptionManager::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    fun decryptStream(
        password: String,
        encryptionResult: FileEncryptionResult,
        settings: SecuritySettings,
        encryptedFile: Path,
        outputStream: OutputStream
    ) {
        try {
            val key: SecretKey = generateSecretKey(
                password = password,
                salt = encryptionResult.salt,
                iterationCount = settings.iterationCount,
                passwordKeySize = settings.passwordKeySizeBytes
            )
            val (cipher, spec) = setupCipher(encryptionResult.nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)

            val messageDigest = getMessageDigest()

            // Verify Integrity
            val cipherInputStream = CipherInputStream(encryptedFile.inputStream(), cipher)
            cipherInputStream.use { encryptedInputStream ->
                val buffer = ByteArray(cipher.blockSize)
                var nread: Int
                while (encryptedInputStream.read(buffer).also { nread = it } > 0) {
                    messageDigest.update(buffer, 0, nread)
                }
            }
            val digest = messageDigest.digest()
            if (!digest.contentEquals(encryptionResult.hashSum)) {
                throw IntegrityFailedException(
                    "Integrity check failed (expected: " +
                        "${encryptionResult.hashSum.contentToString()}, got: ${digest.contentToString()})."
                )
            }
            outputStream.use {
                CipherInputStream(encryptedFile.inputStream(), cipher).use { encryptedInputStream ->
                    val buffer = ByteArray(cipher.blockSize)
                    var nread: Int
                    while (encryptedInputStream.read(buffer).also { nread = it } > 0) {
                        it.write(buffer, 0, nread)
                        messageDigest.update(buffer, 0, nread)
                    }
                    it.flush()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to decrypt file '$encryptedFile'.", e)
            throw EncryptionException("Failed to decrypt file '$encryptedFile'.")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    fun encryptSteam(inputStream: InputStream, path: Path, name: String): Pair<String, FileEncryptionResult> {
        try {
            val password = PasswordManager.createRandomPassword()
            val salt = generateSalt()

            val key: SecretKey = generateSecretKey(
                password = password,
                salt = salt,
                iterationCount = ApplicationConfiguration.securityConfig.defaultIterationCount,
                passwordKeySize = ApplicationConfiguration.securityConfig.defaultPasswordKeySizeBytes
            )

            val nonce = createNonce()

            val (cipher, spec) = setupCipher(nonce)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val digest = getMessageDigest()


            val baos = ByteArrayOutputStream()
            CipherOutputStream(path.outputStream(), cipher).use { encryptedOutputStream ->
                val buffer = ByteArray(cipher.blockSize)
                var nread: Int
                while (inputStream.read(buffer).also { nread = it } > 0) {
                    encryptedOutputStream.write(buffer, 0, nread)
                    digest.update(buffer, 0, nread)
                    baos.write(buffer, 0, nread)
                }
                encryptedOutputStream.flush()
            }
            inputStream.close()

            // Validate mime type
            validateMimeType(baos.toByteArray().inputStream(), name)


            return Pair(
                password, FileEncryptionResult(
                    initializationVector = cipher.iv,
                    nonce = nonce,
                    hashSum = digest.digest(),
                    salt = salt
                )
            )
        } catch (e: ApplicationServiceException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to encrypt stream.", e)
            throw EncryptionException("Failed to encrypt stream.")
        }
    }

    private fun validateMimeType(inputStream: InputStream, name: String?) {
        val tika = TikaConfig()
        val metadata = Metadata()
        val mimetype = tika.detector.detect(TikaInputStream.get(inputStream), metadata)
        val validMimeTypes = ApplicationConfiguration.uploadSettings.validMimeTypes
        if (!validMimeTypes.contains(mimetype.baseType.toString())) {
            throw ApplicationServiceException(errorCode = NOT_ACCEPTED_MIME_TYPE, "Given mimetype of upload '$name' (${mimetype.type}) is not one of ($validMimeTypes).")
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    fun encryptPassword(password: String): PasswordEncryptionResult {
        try {
            val salt = generateSalt()

            val key: SecretKey = generateSecretKey(
                password = password,
                salt = salt,
                iterationCount = ApplicationConfiguration.securityConfig.defaultIterationCount,
                passwordKeySize = ApplicationConfiguration.securityConfig.defaultPasswordKeySizeBytes
            )

            val nonce = createNonce()

            val (cipher, spec) = setupCipher(nonce)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)

            val encryptedPassword = cipher.doFinal(password.encodeToByteArray())

            val messageDigest = getMessageDigest()
            messageDigest.update(encryptedPassword)
            return PasswordEncryptionResult(
                initializationVector = cipher.iv,
                hashSum = messageDigest.digest(),
                nonce = nonce,
                encryptedPassword = encryptedPassword,
                salt = salt
            )
        } catch (e: Exception) {
            logger.error("Failed to encrypt password.", e)
            throw EncryptionException("Failed to encrypt password.")
        }
    }

    private fun createNonce(): ByteArray {
        val random = SecureRandom.getInstanceStrong()
        val nonce = ByteArray(ApplicationConfiguration.securityConfig.defaultNonceLengthBytes)
        random.nextBytes(nonce)
        return nonce
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(ApplicationConfiguration.securityConfig.defaultSaltLengthBytes)
        val random: SecureRandom = SecureRandom.getInstanceStrong()
        random.nextBytes(salt)
        return salt
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    fun decryptString(
        encryptionResult: PasswordEncryptionResult,
        settings: SecuritySettings,
        password: String
    ): String {
        try {
            val key: SecretKey = generateSecretKey(
                password = password,
                salt = encryptionResult.salt,
                iterationCount = settings.iterationCount,
                passwordKeySize = settings.passwordKeySizeBytes
            )

            val messageDigest = getMessageDigest()
            messageDigest.update(encryptionResult.encryptedPassword)
            val digest = messageDigest.digest()
            if (!digest.contentEquals(encryptionResult.hashSum)) {
                throw IntegrityFailedException(
                    "Integrity check failed (expected: " +
                        "${encryptionResult.hashSum.contentToString()}, got: ${digest.contentToString()}."
                )
            }

            val (cipher, spec) = setupCipher(encryptionResult.nonce)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val decryptedPassword = cipher.doFinal(encryptionResult.encryptedPassword)
            return decryptedPassword.decodeToString()
        } catch (e: Exception) {
            logger.error("Failed to decrypt string.", e)
            throw EncryptionException("Failed to decrypt string.")
        }
    }

    private fun setupCipher(nonce: ByteArray): Pair<Cipher, GCMParameterSpec> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(ApplicationConfiguration.securityConfig.defaultGcmParameterSpecLength, nonce)
        return Pair(cipher, spec)
    }

    private fun getMessageDigest(): MessageDigest =
        MessageDigest.getInstance(ApplicationConfiguration.securityConfig.fileIntegrityCheckHashingAlgorithm)

    private fun generateSecretKey(
        password: String,
        salt: ByteArray,
        iterationCount: Int,
        passwordKeySize: Int
    ): SecretKey {
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val passwordBasedEncryptionKeySpec: KeySpec = PBEKeySpec(
            password.toCharArray(), salt, iterationCount,
            passwordKeySize
        )
        val secretKeyFromPBKDF2 = secretKeyFactory.generateSecret(passwordBasedEncryptionKeySpec)
        return SecretKeySpec(secretKeyFromPBKDF2.encoded, "AES")
    }
}