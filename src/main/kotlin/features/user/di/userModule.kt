package com.piedpiper.features.user.di

import com.piedpiper.features.user.data.repository.UserDataRepository
import com.piedpiper.features.user.data.services.UserService
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.dsl.module

val userModule = module {
    single<HttpClient> {
        HttpClient(Apache) {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    single<UserDataRepository> {
        UserService(get(), "http://0.0.0.0:8081/PiedPiper/api/v1")
    }
}
