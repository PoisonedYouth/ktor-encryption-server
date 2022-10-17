package com.poisonedyouth.persistence

import com.poisonedyouth.domain.User
import com.poisonedyouth.domain.UserSettings
import com.poisonedyouth.security.PasswordEncryptionResult
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

interface UserRepository {
    fun save(user: User): User

    fun delete(username: String)

    fun findByUsername(username: String): User?

    fun findAll(): List<User>
}

class UserRepositoryImpl : UserRepository {
    private val logger: Logger = LoggerFactory.getLogger(UserRepository::class.java)

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun save(user: User): User = transaction {
        try {
            val currentDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
            val existingUser = UserEntity.find { UserTable.username eq user.username }.firstOrNull()
            if (existingUser == null) {
                UserEntity.new {
                    username = user.username
                    password = user.encryptionResult.encryptedPassword
                    iv = user.encryptionResult.initializationVector
                    hashSum = user.encryptionResult.hashSum
                    nonce = user.encryptionResult.nonce
                    created = currentDateTime
                    lastUpdated = currentDateTime
                    salt = user.encryptionResult.salt
                    userSettings = UserSettingsEntity.new {
                        uploadFileExpirationDays = user.userSettings.uploadFileExpirationDays
                        created = currentDateTime
                        lastUpdated = created
                    }
                    securitySettings = SecuritySettingsEntity.newFromSecuritySettings(user.securitySettings)
                }
                user.copy(
                    created = currentDateTime,
                    lastUpdated = currentDateTime
                )
            } else {
                existingUser.username = user.username
                existingUser.password = user.encryptionResult.encryptedPassword
                existingUser.iv = user.encryptionResult.initializationVector
                existingUser.hashSum = user.encryptionResult.hashSum
                existingUser.nonce = user.encryptionResult.nonce
                existingUser.created = user.created
                existingUser.lastUpdated = currentDateTime
                existingUser.salt = user.encryptionResult.salt
                existingUser.userSettings = existingUser.userSettings.apply {
                    this.lastUpdated = currentDateTime
                    this.uploadFileExpirationDays = user.userSettings.uploadFileExpirationDays
                }
                user.copy(
                    lastUpdated = currentDateTime
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to save user '$user' to database.", e)
            throw PersistenceException("Failed to save user '$user' to database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun delete(username: String): Unit = transaction {
        try {
            UserEntity.find { UserTable.username eq username }.firstOrNull().let {
                if (it == null) {
                    error("User with username '${username}' does not exist!")
                } else {
                    it.delete()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete user with username '$username' from database.", e)
            throw PersistenceException("Failed to delete user with username '$username' from database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findByUsername(username: String): User? = transaction {
        try {
            UserEntity.findUserOrNull(username)?.toUser()
        } catch (e: Exception) {
            logger.error("Failed to find with username '$username' in database.", e)
            throw PersistenceException("Failed to find with username '$username' in database.", e)
        }
    }

    @SuppressWarnings("TooGenericExceptionCaught") // It's intended to catch all exceptions in this layer
    override fun findAll(): List<User> = transaction {
        try {
            UserEntity.findAll().map { it.toUser() }
        } catch (e: Exception) {
            logger.error("Failed to find all user in database.", e)
            throw PersistenceException("Failed to find all user in database.", e)
        }
    }
}

fun UserEntity.toUser(): User {
    return User(
        username = this.username,
        encryptionResult = PasswordEncryptionResult(
            initializationVector = this.iv,
            encryptedPassword = this.password,
            hashSum = this.hashSum,
            nonce = this.nonce,
            salt = this.salt
        ),
        userSettings = this.userSettings.toUserSettings(),
        securitySettings = this.securitySettings.toSecuritySettings(),
        created = this.created.truncatedTo(ChronoUnit.SECONDS),
        lastUpdated = this.lastUpdated.truncatedTo(ChronoUnit.SECONDS),
    )
}

fun UserSettingsEntity.toUserSettings(): UserSettings {
    return UserSettings(
        uploadFileExpirationDays = this.uploadFileExpirationDays,
        created = this.created.truncatedTo(ChronoUnit.SECONDS),
        lastUpdated = this.created.truncatedTo(ChronoUnit.SECONDS)
    )
}

