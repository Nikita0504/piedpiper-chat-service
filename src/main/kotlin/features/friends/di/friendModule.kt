package com.piedpiper.features.friends.di

import com.piedpiper.features.friends.data.repository.FriendRepository
import com.piedpiper.features.friends.data.services.FriendService
import org.koin.dsl.module

val friendModule = module {
    single<FriendRepository> {
        FriendService(
            dataBase = get(),
            userRepository = get(),
        )
    }
}

