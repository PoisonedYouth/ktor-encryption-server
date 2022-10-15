package com.poisonedyouth.security

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
import java.io.File
import java.io.InputStream
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

    fun decryptStream(
        password: String,
        encryptionResult: FileEncryptionResult,
        settings: SecuritySettings,
        encryptedFile: File,
        outputFile: File
    ): File {
        val key: SecretKey = generateSecretKey(
            password = password,
            salt = encryptionResult.salt,
            iterationCount = settings.iterationCount,
            passwordKeySize = settings.passwordKeySize
        )
        val (cipher, spec) = setupCipher(encryptionResult.nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val messageDigest = getMessageDigest()

        val cipherInputStream = CipherInputStream(encryptedFile.inputStream(), cipher)
        outputFile.outputStream().use {
            cipherInputStream.use { encryptedInputStream ->
                val buffer = ByteArray(cipher.blockSize)
                var nread: Int
                while (encryptedInputStream.read(buffer).also { nread = it } > 0) {
                    it.write(buffer, 0, nread)
                    messageDigest.update(buffer, 0, nread)
                }
                it.flush()
            }
        }
        val digest = messageDigest.digest()
        if (!digest.contentEquals(encryptionResult.hashSum)) {
            throw IntegrityFailedException(
                "Integrity check failed (expected: " +
                    "${encryptionResult.hashSum.contentToString()}, got: ${digest.contentToString()})."
            )
        }
        return outputFile
    }

    fun encryptSteam(inputstream: InputStream, file: File): Pair<String, FileEncryptionResult> {
        val password = PasswordManager.createRandomPassword()
        val salt = generateSalt()

        val key: SecretKey = generateSecretKey(
            password = password,
            salt = salt,
            iterationCount = ApplicationConfiguration.securityConfig.defaultIterationCount,
            passwordKeySize = ApplicationConfiguration.securityConfig.defaultPasswordKeySize
        )

        val nonce = createNonce()

        val (cipher, spec) = setupCipher(nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val digest = getMessageDigest()

        CipherOutputStream(file.outputStream(), cipher).use { encryptedOutputStream ->
            val buffer = ByteArray(cipher.blockSize)
            var nread: Int
            while (inputstream.read(buffer).also { nread = it } > 0) {
                encryptedOutputStream.write(buffer, 0, nread)
                digest.update(buffer, 0, nread)
            }
            encryptedOutputStream.flush()
        }
        inputstream.close()
        return Pair(
            password, FileEncryptionResult(
                initializationVector = cipher.iv,
                nonce = nonce,
                hashSum = digest.digest(),
                salt = salt
            )
        )
    }

    fun encryptPassword(password: String): PasswordEncryptionResult {
        val salt = generateSalt()

        val key: SecretKey = generateSecretKey(
            password = password,
            salt = salt,
            iterationCount = ApplicationConfiguration.securityConfig.defaultIterationCount,
            passwordKeySize = ApplicationConfiguration.securityConfig.defaultPasswordKeySize
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
    }

    private fun createNonce(): ByteArray {
        val random = SecureRandom.getInstanceStrong()
        val nonce = ByteArray(ApplicationConfiguration.securityConfig.defaultNonceLength)
        random.nextBytes(nonce)
        return nonce
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(ApplicationConfiguration.securityConfig.defaultSaltLength)
        val random: SecureRandom = SecureRandom.getInstanceStrong()
        random.nextBytes(salt)
        return salt
    }

    fun decryptString(
        encryptionResult: PasswordEncryptionResult,
        settings: SecuritySettings,
        password: String
    ): String {
        val key: SecretKey = generateSecretKey(
            password = password,
            salt = encryptionResult.salt,
            iterationCount = settings.iterationCount,
            passwordKeySize = settings.passwordKeySize
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