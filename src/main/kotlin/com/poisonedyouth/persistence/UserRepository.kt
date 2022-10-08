package com.poisonedyouth.persistence

import com.poisonedyouth.domain.User
import com.poisonedyouth.security.PasswordEncryptionResult
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

interface UserRepository {
    fun save(user: User): User

    fun delete(username: String)

    fun findByUsername(username: String): User?

    fun findAll(): List<User>
}

class UserRepositoryImpl : UserRepository {

    override fun save(user: User): User = transaction {
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
            user.copy(
                lastUpdated = currentDateTime
            )
        }
    }

    override fun delete(username: String): Unit = transaction {
        UserEntity.find { UserTable.username eq username }.firstOrNull().let {
            if (it == null) {
                error("User with username '${username}' does not exist!")
            } else {
                it.delete()
            }
        }
    }

    override fun findByUsername(username: String): User? = transaction {
        UserEntity.find { UserTable.username eq username }.firstOrNull()?.toUser()
    }

    override fun findAll(): List<User> = transaction {
        UserEntity.all().map { it.toUser() }
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
        created = this.created.truncatedTo(ChronoUnit.SECONDS),
        lastUpdated = this.lastUpdated.truncatedTo(ChronoUnit.SECONDS),
    )
}
