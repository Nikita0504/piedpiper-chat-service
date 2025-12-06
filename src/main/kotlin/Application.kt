package com.piedpiper

import com.piedpiper.plugins.configureRouting
import com.piedpiper.plugins.configureSerialization
import com.piedpiper.plugins.configureSockets
import io.ktor.server.application.*
import com.piedpiper.plugins.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureFrameworks()
    configureSockets()
    configureSerialization()
    configureRouting()
    configureLogging()
    configureTokenExtraction()
}
