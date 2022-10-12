package com.poisonedyouth.persistence

import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.javatime.datetime

class UploadFileHistoryEntity(id: EntityID<Long>) : LongEntity(id) {
    var ipAddress by UploadFileHistoryTable.ipAddress
    var created by UploadFileHistoryTable.created
    var action by UploadFileHistoryTable.action
    var uploadFile by UploadFileEntity referencedOn UploadFileHistoryTable.uploadFile

    companion object : LongEntityClass<UploadFileHistoryEntity>(UploadFileHistoryTable)
}


object UploadFileHistoryTable : LongIdTable("upload_file_history", "id") {
    val ipAddress = varchar("ip_address", 256)
    val created = datetime("created")
    val action = varchar("action", 256)
    val uploadFile = reference("upload_file_id", UploadFileTable)
}