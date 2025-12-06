package com.piedpiper.features.database.di

import com.piedpiper.common.Parameters
import com.mongodb.reactivestreams.client.MongoClient
import org.koin.dsl.module
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo

val dataBaseModule = module {
    single<MongoClient> {
        KMongo.createClient(
            connectionString = "mongodb://localhost:27017"
        )
    }

    single<CoroutineDatabase> {
        get<MongoClient>().getDatabase(Parameters().DATABASE).coroutine
    }
}