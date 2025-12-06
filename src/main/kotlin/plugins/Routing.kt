package com.piedpiper.plugins

import com.piedpiper.features.chat.chatRoute
import com.piedpiper.features.friends.friendRoute
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/PiedPiper/api/v1/chat") {
            chatRoute()
        }
        route("/PiedPiper/api/v1") {
            friendRoute()
        }
    }
}
