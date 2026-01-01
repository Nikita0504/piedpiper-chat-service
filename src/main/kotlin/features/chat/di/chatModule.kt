package com.piedpiper.features.chat.di

import com.piedpiper.features.chat.data.repository.ChatRepository
import com.piedpiper.features.chat.data.repository.MessageRepository
import com.piedpiper.features.chat.data.services.ChatService
import com.piedpiper.features.chat.data.services.MessageService
import org.koin.dsl.module

val chatModule = module {
    single<ChatRepository> {
        ChatService(
            database = get(),
            userRepository = get(),
        )
    }

    single<MessageRepository> {
        MessageService(
            database = get(),
        )
    }
}