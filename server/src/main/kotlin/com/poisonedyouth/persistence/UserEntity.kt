package com.poisonedyouth.persistence

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime

const val DEFAULT_VARCHAR_COLUMN_LENGTH = 255

class UserEntity(id: EntityID<Long>) : LongEntity(id) {
    var username by UserTable.username
    var password by UserTable.password
    var iv by UserTable.iv
    var nonce by UserTable.nonce
    var salt by UserTable.salt
    var hashSum by UserTable.hashSum
    var created by UserTable.created
    var lastUpdated by UserTable.lastUpdated
    var userSettings by UserSettingsEntity referencedOn UserTable.userSettings
    var securitySettings by SecuritySettingsEntity referencedOn UserTable.securitySettings

    companion object : LongEntityClass<UserEntity>(UserTable) {
        fun getUserOrThrow(username: String): UserEntity {
            return UserEntity.find { UserTable.username eq username }.firstOrNull()
                ?: error("No user available for username '$username'.")
        }

        fun findUserOrNull(username: String): UserEntity? {
            return UserEntity.find { UserTable.username eq username }.firstOrNull()
        }

        fun findAll() = UserEntity.all()
    }
}

object UserTable : LongIdTable("app_user", "id") {
    val username = varchar("username", DEFAULT_VARCHAR_COLUMN_LENGTH).uniqueIndex()
    val password = binary("password")
    val salt = binary("salt")
    val iv = binary("iv")
    val hashSum = binary("hash_sum")
    val nonce = binary("nonce")
    val created = datetime("created")
    val lastUpdated = datetime("last_updated")
    val userSettings = reference("user_settings_id", UserSettingsTable)
    val securitySettings = reference("security_settings_id", SecuritySettingsTable)
}