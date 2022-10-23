package com.poisonedyouth

import com.poisonedyouth.application.deleteDirectoryRecursively
import com.poisonedyouth.configuration.ApplicationConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.nio.file.Files
import java.nio.file.Path

class KtorServerExtension : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {
    companion object {
        private lateinit var server: NettyApplicationEngine

        val basePath: Path = Files.createTempDirectory("ktor-encryption-server")
    }

    override fun beforeAll(context: ExtensionContext?) {
        val env = applicationEngineEnvironment {
            config = ApplicationConfig("application-test.conf")
            // Public API
            connector {
                host = "0.0.0.0"
                port = 8080
            }
        }
        server = embeddedServer(Netty, env).start(false)
    }

    override fun beforeEach(context: ExtensionContext?) {
        deleteDirectoryRecursively(basePath)
        Files.createDirectory(basePath)
        mockkObject(ApplicationConfiguration)
        every {
            ApplicationConfiguration.getUploadDirectory()
        } returns basePath
    }

    override fun afterEach(context: ExtensionContext?) {
        deleteDirectoryRecursively(basePath)
        unmockkObject(ApplicationConfiguration)

    }

    override fun afterAll(context: ExtensionContext?) {
        server.stop(100, 100)
    }
}

fun createHttpClient(username: String = "username", password: String = "password"): HttpClient {
    val client = HttpClient {
        install(ContentNegotiation) {
            jackson()
        }
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(username = username, password = password)
                }
                realm = "ktor-encryption-server"
            }
        }
    }
    return client
}