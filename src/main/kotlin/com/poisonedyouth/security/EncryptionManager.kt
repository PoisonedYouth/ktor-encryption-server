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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val passwordBasedEncryptionKeySpec: KeySpec =
            PBEKeySpec(password.toCharArray(), encryptionResult.salt, 10000, 256)
        val secretKeyFromPBKDF2 = secretKeyFactory.generateSecret(passwordBasedEncryptionKeySpec)
        val key: SecretKey = SecretKeySpec(secretKeyFromPBKDF2.encoded, "AES")
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
        val salt = ByteArray(64)
        val random: SecureRandom = SecureRandom.getInstanceStrong()
        random.nextBytes(salt)

        // DERIVE key (from password and salt)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val passwordBasedEncryptionKeySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val secretKeyFromPBKDF2 = secretKeyFactory.generateSecret(passwordBasedEncryptionKeySpec)
        val key: SecretKey = SecretKeySpec(secretKeyFromPBKDF2.encoded, "AES")

        // GENERATE random nonce (number used once)
        val nonce = ByteArray(32)
        random.nextBytes(nonce)

        // SET UP CIPHER for encryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(16 * 8, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        // SET UP HASHING OF FILE
        val digest = MessageDigest.getInstance("SHA-512")

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
        val salt = ByteArray(64)
        val random: SecureRandom = SecureRandom.getInstanceStrong()
        random.nextBytes(salt)

        // DERIVE key (from password and salt)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val passwordBasedEncryptionKeySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val secretKeyFromPBKDF2 = secretKeyFactory.generateSecret(passwordBasedEncryptionKeySpec)
        val key: SecretKey = SecretKeySpec(secretKeyFromPBKDF2.encoded, "AES")

        // GENERATE random nonce (number used once)
        val nonce = ByteArray(32)
        random.nextBytes(nonce)

        // SET UP CIPHER for encryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(16 * 8, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)

        val messageDigest = MessageDigest.getInstance("SHA-512")
        val cipherText = cipher.doFinal()
        messageDigest.update(cipherText)
        return PasswordEncryptionResult(
            initializationVector = cipher.iv,
            hashSum = messageDigest.digest(),
            nonce = nonce,
            encryptedPassword = cipherText,
            salt = salt
        )
    }

    fun decryptString(encryptionResult: PasswordEncryptionResult, password: String): String {
        // DERIVE key (from password and salt)
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val passwordBasedEncryptionKeySpec: KeySpec =
            PBEKeySpec(password.toCharArray(), encryptionResult.salt, 10000, 256)
        val secretKeyFromPBKDF2 = secretKeyFactory.generateSecret(passwordBasedEncryptionKeySpec)
        val key: SecretKey = SecretKeySpec(secretKeyFromPBKDF2.encoded, "AES")


        // SET UP CIPHER for decryption
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(16 * 8, encryptionResult.nonce)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(encryptionResult.encryptedPassword).decodeToString()
    }
}