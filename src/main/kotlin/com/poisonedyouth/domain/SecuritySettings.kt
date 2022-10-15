package com.poisonedyouth.domain

import com.poisonedyouth.configuration.ApplicationConfiguration

data class SecuritySettings(
    var fileIntegrityCheckHashingAlgorithm: String,
    var passwordKeySize: Int,
    var nonceLength: Int,
    var saltLength: Int,
    var iterationCount: Int,
    var gcmParameterSpecLength: Int
)

fun defaultSecurityFileSettings() = SecuritySettings(
    fileIntegrityCheckHashingAlgorithm = ApplicationConfiguration.securityConfig.fileIntegrityCheckHashingAlgorithm,
    passwordKeySize = ApplicationConfiguration.securityConfig.defaultPasswordKeySize,
    nonceLength = ApplicationConfiguration.securityConfig.defaultNonceLength,
    saltLength = ApplicationConfiguration.securityConfig.defaultSaltLength,
    iterationCount = ApplicationConfiguration.securityConfig.defaultIterationCount,
    gcmParameterSpecLength = ApplicationConfiguration.securityConfig.defaultGcmParameterSpecLength
)
