package com.poisonedyouth.dependencyinjection

import com.poisonedyouth.api.UserController
import com.poisonedyouth.api.UserControllerImpl
import com.poisonedyouth.application.FileEncryptionService
import com.poisonedyouth.application.FileEncryptionServiceImpl
import com.poisonedyouth.application.FileHandler
import com.poisonedyouth.application.FileHandlerImpl
import com.poisonedyouth.application.UploadFileHistoryService
import com.poisonedyouth.application.UploadFileHistoryServiceImpl
import com.poisonedyouth.application.UserService
import com.poisonedyouth.application.UserServiceImpl
import com.poisonedyouth.expiration.CleanupTask
import com.poisonedyouth.expiration.UploadFileExpirationTask
import com.poisonedyouth.persistence.UploadFileHistoryRepository
import com.poisonedyouth.persistence.UploadFileHistoryRepositoryImpl
import com.poisonedyouth.persistence.UploadFileRepository
import com.poisonedyouth.persistence.UploadFileRepositoryImpl
import com.poisonedyouth.persistence.UserRepository
import com.poisonedyouth.persistence.UserRepositoryImpl
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.SLF4JLogger

val encryptionServerModule = module {
    single<UserRepository> { UserRepositoryImpl() }
    single<FileEncryptionService> { FileEncryptionServiceImpl(get()) }
    single<UploadFileRepository> { UploadFileRepositoryImpl() }
    single<UserService> { UserServiceImpl(get(), get()) }
    single<FileHandler> { FileHandlerImpl(get(), get(), get(), get()) }
    single { UploadFileExpirationTask(get()) }
    single { CleanupTask(get())}
    single<UploadFileHistoryRepository> { UploadFileHistoryRepositoryImpl() }
    single<UploadFileHistoryService> { UploadFileHistoryServiceImpl(get(), get(), get()) }
    single<UserController>{ UserControllerImpl(get()) }
}

fun Application.setupKoin() {
    install(Koin) {
        SLF4JLogger()
        modules(encryptionServerModule)
    }
}
