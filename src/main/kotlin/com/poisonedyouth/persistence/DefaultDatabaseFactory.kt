package com.poisonedyouth.persistence

import com.poisonedyouth.configuration.ApplicationConfiguration
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

fun setupDatabase(appConfig: ApplicationConfiguration): HikariDataSource {
    val defaultDatabaseFactory = DefaultDatabaseFactory(appConfig)
    defaultDatabaseFactory.connect()
    return defaultDatabaseFactory.dataSource
}

class DefaultDatabaseFactory(appConfig: ApplicationConfiguration) : DatabaseFactory {

    private val dbConfig = appConfig.databaseConfig
    lateinit var dataSource: HikariDataSource

    override fun connect() {
        dataSource = hikari()
        Database.connect(dataSource)
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = dbConfig.driverClass
        config.jdbcUrl = dbConfig.url
        config.username = dbConfig.user
        config.password = dbConfig.password
        config.maximumPoolSize = dbConfig.maxPoolSize
        config.isAutoCommit = true
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }
}
