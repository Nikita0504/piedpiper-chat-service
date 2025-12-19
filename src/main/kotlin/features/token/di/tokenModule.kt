package com.piedpiper.features.token.di

import com.piedpiper.common.Parameters
import com.piedpiper.features.token.data.repository.TokenRepository
import com.piedpiper.features.token.data.services.TokenService
import org.koin.dsl.module

val tokenModule = module {
    single<TokenRepository> {
        TokenService(
            client = get(),
            userServiceBaseUrl = Parameters().AUTH_SERVICE
        )
    }
}