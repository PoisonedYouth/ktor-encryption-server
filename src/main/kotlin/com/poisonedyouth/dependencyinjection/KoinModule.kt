package com.poisonedyouth.dependencyinjection

import com.poisonedyouth.application.FileEncryptionService
import com.poisonedyouth.application.FileEncryptionServiceImpl
import com.poisonedyouth.application.FileHandler
import com.poisonedyouth.application.FileHandlerImpl
import com.poisonedyouth.application.UserService
import com.poisonedyouth.application.UserServiceImpl
import com.poisonedyouth.expiration.UploadFileExpirationTask
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UploadFileRepositoryImpl
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.persistence.UserRepositoryImpl
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.SLF4JLogger

val bankingAppModule = module {
    single<UserRepository> { UserRepositoryImpl() }
    single<FileEncryptionService> { FileEncryptionServiceImpl(get()) }
    single<UploadFileRepository> { UploadFileRepositoryImpl() }
    single<UserService> { UserServiceImpl(get(), get()) }
    single<FileHandler> { FileHandlerImpl(get(), get(), get()) }
    single { UploadFileExpirationTask(get()) }
}

fun Application.setupKoin() {
    install(Koin) {
        SLF4JLogger()
        modules(bankingAppModule)
    }
}
