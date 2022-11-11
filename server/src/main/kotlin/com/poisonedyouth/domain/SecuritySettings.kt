package com.poisonedyouth.domain

import com.poisonedyouth.configuration.ApplicationConfiguration

data class SecuritySettings(
    var fileIntegrityCheckHashingAlgorithm: String,
    var passwordKeySizeBytes: Int,
    var nonceLengthBytes: Int,
    var saltLengthBytes: Int,
    var iterationCount: Int,
    var gcmParameterSpecLength: Int
)

fun defaultSecurityFileSettings() = SecuritySettings(
    fileIntegrityCheckHashingAlgorithm = ApplicationConfiguration.securityConfig.fileIntegrityCheckHashingAlgorithm,
    passwordKeySizeBytes = ApplicationConfiguration.securityConfig.defaultPasswordKeySizeBytes,
    nonceLengthBytes = ApplicationConfiguration.securityConfig.defaultNonceLengthBytes,
    saltLengthBytes = ApplicationConfiguration.securityConfig.defaultSaltLengthBytes,
    iterationCount = ApplicationConfiguration.securityConfig.defaultIterationCount,
    gcmParameterSpecLength = ApplicationConfiguration.securityConfig.defaultGcmParameterSpecLength
)
