package com.poisonedyouth.persistence

import com.zaxxer.hikari.HikariDataSource
import liquibase.Liquibase
import liquibase.configuration.LiquibaseConfiguration
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor

fun migrateDatabaseSchema(datasource: HikariDataSource) {
    val database = DatabaseFactory.getInstance()
        .findCorrectDatabaseImplementation(
            JdbcConnection(datasource.connection)
        )
    val liquibase = Liquibase(
        "db/changelog.sql",
        ClassLoaderResourceAccessor(
            LiquibaseConfiguration::class.java.classLoader
        ),
        database
    )
    liquibase.update("")
    liquibase.close()
}
