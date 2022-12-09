package com.poisonedyouth

import com.poisonedyouth.configuration.ApplicationConfiguration
import com.poisonedyouth.configuration.setupApplicationConfiguration
import com.poisonedyouth.dependencyinjection.setupKoin
import com.poisonedyouth.expiration.setupCleanupTask
import com.poisonedyouth.expiration.setupUploadFileExpirationTask
import com.poisonedyouth.persistence.migrateDatabaseSchema
import com.poisonedyouth.persistence.setupDatabase
import com.poisonedyouth.plugins.*
import io.ktor.server.application.Application
import io.ktor.server.engine.*

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents
// the IDE from marking it as unused.
fun Application.module() {
    setupApplicationConfiguration()
    val dataSource = setupDatabase(ApplicationConfiguration)
    migrateDatabaseSchema(dataSource)

    setupKoin()
    configureSerialization()
    configureSecurity()
    configureRouting()
    configureCallLogging()
    configureOpenApi()

    setupUploadFileExpirationTask()
    setupCleanupTask()
}
