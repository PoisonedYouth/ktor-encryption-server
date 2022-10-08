package com.poisonedyouth.persistence

import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.FileSystemResourceAccessor
import java.io.File

fun migrateDatabaseSchema(datasource: HikariDataSource){
    val database = DatabaseFactory.getInstance()
        .findCorrectDatabaseImplementation(
            JdbcConnection(datasource.connection)
        )
    val liquibase = Liquibase(
        "/db/changelog.sql",
        FileSystemResourceAccessor(File("src/main/resources")),
        database
    )
    liquibase.update("")
    liquibase.close()
}
