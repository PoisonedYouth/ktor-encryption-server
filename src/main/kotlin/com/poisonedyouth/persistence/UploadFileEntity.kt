package com.poisonedyouth.persistence

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime

class UploadFileEntity(id: EntityID<Long>) : LongEntity(id) {
    var filename by UploadFileTable.filename
    var encryptedFilename by UploadFileTable.encryptedFilename
    var hashSum by UploadFileTable.hashSum
    var nonce by UploadFileTable.nonce
    var iv by UploadFileTable.iv
    var salt by UploadFileTable.salt
    var created by UploadFileTable.created
    var user by UserEntity referencedOn UploadFileTable.user
    var settings by SecuritySettingsEntity referencedOn UploadFileTable.settings

    companion object : LongEntityClass<UploadFileEntity>(UploadFileTable)
}

object UploadFileTable : LongIdTable("upload_file", "id") {
    val filename = varchar("filename", DEFAULT_VARCHAR_COLUMN_LENGTH)
    val encryptedFilename = varchar("encrypted_filename", DEFAULT_VARCHAR_COLUMN_LENGTH).uniqueIndex()
    val hashSum = binary("hash_sum")
    val nonce = binary("nonce")
    val salt = binary("salt")
    val iv = binary("iv")
    val created = datetime("created")
    val user = reference("user_id", UserTable)
    val settings = reference("settings_id", SecuritySettingsTable)
}