package com.piedpiper.features.database.di

import com.piedpiper.common.Parameters
import com.piedpiper.features.database.Chats
import com.piedpiper.features.database.ChatUsers
import com.piedpiper.features.database.FriendLists
import com.piedpiper.features.database.Messages
import com.piedpiper.features.database.UserInChats
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.dsl.module

val dataBaseModule = module {
    single<Database> {
        val parameters = Parameters()
        val database = Database.connect(
            url = parameters.POSTGRES_URL,
            driver = "org.postgresql.Driver",
            user = parameters.POSTGRES_USER,
            password = parameters.POSTGRES_PASSWORD,
        )

        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                Chats,
                ChatUsers,
                UserInChats,
                Messages,
                FriendLists
            )
        }

        database
    }
}
