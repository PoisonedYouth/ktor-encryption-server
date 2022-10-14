package com.poisonedyouth.persistence

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime

class UserSettingsEntity(id: EntityID<Long>) : LongEntity(id) {
    var uploadFileExpirationDays by UserSettingsTable.uploadFileExpirationDays
    var created by UserSettingsTable.created
    var lastUpdated by UserSettingsTable.lastUpdated

    companion object : LongEntityClass<UserSettingsEntity>(UserSettingsTable)
}

object UserSettingsTable : LongIdTable("app_user_settings", "id") {
    val uploadFileExpirationDays = long("upload_file_expiration_days")
    val created = datetime("created")
    val lastUpdated = datetime("last_updated")
}