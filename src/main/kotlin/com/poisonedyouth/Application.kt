package com.poisonedyouth

import com.poisonedyouth.configuration.setupApplicationConfiguration
import com.poisonedyouth.dependencyinjection.setupKoin
import com.poisonedyouth.expiration.setupUploadFileExpirationTask
import com.poisonedyouth.persistence.migrateDatabaseSchema
import com.poisonedyouth.persistence.setupDatabase
import com.poisonedyouth.plugins.configureRouting
import com.poisonedyouth.plugins.configureSecurity
import com.poisonedyouth.plugins.configureSerialization
import io.ktor.server.application.Application

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents
// the IDE from marking it as unused.
fun Application.module() {
    val applicationConfiguration = setupApplicationConfiguration()
    val dataSource = setupDatabase(applicationConfiguration)
    migrateDatabaseSchema(dataSource)

    setupKoin()
    configureSerialization()
    configureSecurity()
    configureRouting()

    setupUploadFileExpirationTask()
}
