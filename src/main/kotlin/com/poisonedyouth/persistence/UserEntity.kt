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

    companion object : LongEntityClass<UserEntity>(UserTable) {
        fun findUserOrThrow(username: String): UserEntity {
            return UserEntity.find { UserTable.username eq username }.firstOrNull()
                ?: error("No user available for username '$username'.")
        }
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
}