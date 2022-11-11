package com.poisonedyouth.persistence

import com.poisonedyouth.domain.SecuritySettings
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable

class SecuritySettingsEntity(id: EntityID<Long>) : LongEntity(id) {
    var fileIntegrityCheckHashingAlgorithm by SecuritySettingsTable.fileIntegrityCheckHashingAlgorithm
    var passwordKeySizeBytes by SecuritySettingsTable.passwordKeySizeBytes
    var nonceLengthBytes by SecuritySettingsTable.nonceLengthBytes
    var saltLengthBytes by SecuritySettingsTable.saltLengthBytes
    var iterationCount by SecuritySettingsTable.iterationCount
    var gcmParameterSpecLength by SecuritySettingsTable.gcmParameterSpecLength

    companion object : LongEntityClass<SecuritySettingsEntity>(SecuritySettingsTable) {
        fun newFromSecuritySettings(securitySettings: SecuritySettings) = SecuritySettingsEntity.new {
            fileIntegrityCheckHashingAlgorithm = securitySettings.fileIntegrityCheckHashingAlgorithm
            passwordKeySizeBytes = securitySettings.passwordKeySizeBytes
            nonceLengthBytes = securitySettings.nonceLengthBytes
            saltLengthBytes = securitySettings.saltLengthBytes
            iterationCount = securitySettings.iterationCount
            gcmParameterSpecLength = securitySettings.gcmParameterSpecLength
        }
    }
}


object SecuritySettingsTable : LongIdTable("security_settings", "id") {
    val fileIntegrityCheckHashingAlgorithm = varchar("integrity_check_hashing_algorithm", 256)
    val passwordKeySizeBytes = integer("password_key_size")
    val nonceLengthBytes = integer("nonce_length")
    val saltLengthBytes = integer("salt_length")
    val iterationCount = integer("iteration_count")
    val gcmParameterSpecLength = integer("gcm_parameter_spec_length")
}