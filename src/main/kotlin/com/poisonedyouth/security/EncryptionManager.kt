package com.poisonedyouth.security

import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.*


data class FileEncryptionResult(
    val initializationVector: ByteArray,
    val nonce: ByteArray,
    val hashSum: ByteArray,
    val salt: ByteArray,
)

data class PasswordEncryptionResult(
    val initializationVector: ByteArray,
    val nonce: ByteArray,
    val hashSum: ByteArray,
    val encryptedPassword: ByteArray,
    val salt: ByteArray
)


object EncryptionManager {

    fun decryptStream(
        outputStream: OutputStream,
        password: String,
        encryptionResult: FileEncryptionResult,
        file: File
    ) {
        // DERIVE key (from password and salt)
        val key: SecretKey = generateSecretKey(password, encryptionResult.salt)
        // READ ENCRYPTED FILE
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(16 * 8, encryptionResult.nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val cipherInputStream = CipherInputStream(file.inputStream(), cipher)
        outputStream.use {
            cipherInputStream.use { encryptedInputStream ->
                val buffer = ByteArray(cipher.blockSize)
                var nread: Int
                while (encryptedInputStream.read(buffer).also { nread = it } > 0) {
                    it.write(buffer, 0, nread);
                }
                it.flush();
            }
        }
    }

    fun encryptSteam(inputstream: InputStream, file: File): Pair<String, FileEncryptionResult> {
        // GENERATE password
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        val password = Base64.getEncoder().encodeToString(keyGen.generateKey().encoded)

        // GENERATE random salt
        val salt = generateSalt()

        // DERIVE key (from password and salt)
        val key: SecretKey = generateSecretKey(password, salt)

        // GENERATE random nonce (number used once)
        val nonce = createNonce()

        // SET UP CIPHER for encryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(16 * 8, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        // SET UP HASHING OF FILE
        val digest = getMessageDigest()

        // SET UP OUTPUT STREAM and write content of String
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
        // GENERATE random salt
        val salt = generateSalt()

        // DERIVE key (from password and salt)
        val key: SecretKey = generateSecretKey(password, salt)

        // GENERATE random nonce (number used once)
        val nonce = createNonce()

        // SET UP CIPHER for encryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(16 * 8, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val messageDigest = getMessageDigest()
        val encryptedPassword = cipher.doFinal()
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
        val nonce = ByteArray(32)
        random.nextBytes(nonce)
        return nonce
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(64)
        val random: SecureRandom = SecureRandom.getInstanceStrong()
        random.nextBytes(salt)
        return salt
    }

    fun decryptString(encryptionResult: PasswordEncryptionResult, password: String): String {
        // DERIVE key (from password and salt)
        val key: SecretKey = generateSecretKey(password, encryptionResult.salt)

        val messageDigest = getMessageDigest()
        messageDigest.update(encryptionResult.encryptedPassword)
        if (!messageDigest.digest().contentEquals(encryptionResult.hashSum)) {
            throw IntegrityFailedException("Integrity check failed.")
        }

        // SET UP CIPHER for decryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(16 * 8, encryptionResult.nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(encryptionResult.encryptedPassword).decodeToString()
    }

    private fun getMessageDigest(): MessageDigest = MessageDigest.getInstance("SHA-512")

    private fun generateSecretKey(password: String, salt: ByteArray): SecretKey {
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val passwordBasedEncryptionKeySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val secretKeyFromPBKDF2 = secretKeyFactory.generateSecret(passwordBasedEncryptionKeySpec)
        val key: SecretKey = SecretKeySpec(secretKeyFromPBKDF2.encoded, "AES")
        return key
    }
}