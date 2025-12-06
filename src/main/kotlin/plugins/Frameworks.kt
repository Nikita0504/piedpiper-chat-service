package com.piedpiper.plugins

import com.piedpiper.features.chat.di.chatModule
import com.piedpiper.features.database.di.dataBaseModule
import com.piedpiper.features.friends.di.friendModule
import com.piedpiper.features.token.di.tokenModule
import com.piedpiper.features.user.di.userModule
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()

        modules(
            listOf(
                dataBaseModule,
                userModule,
                tokenModule,
                chatModule,
                friendModule
            )
        )
    }
}
