package com.poisonedyouth

import com.poisonedyouth.application.deleteDirectoryStream
import com.poisonedyouth.configuration.ApplicationConfiguration
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
import java.nio.file.Paths
import java.util.*

class KtorServerExtension : BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {
    companion object {
        private lateinit var server: NettyApplicationEngine

        val basePath: Path = Paths.get(UUID.randomUUID().toString())
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
        Files.createDirectory(basePath)
        mockkObject(ApplicationConfiguration)
        every {
            ApplicationConfiguration.getUploadDirectory()
        } returns basePath
    }

    override fun afterEach(context: ExtensionContext?) {
        deleteDirectoryStream(basePath)
        unmockkObject(ApplicationConfiguration)

    }

    override fun afterAll(context: ExtensionContext?) {
        server.stop(100, 100)
    }
}