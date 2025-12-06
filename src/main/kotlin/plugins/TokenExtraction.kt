package com.piedpiper.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.util.*

val JwtTokenKey = AttributeKey<String>("JwtToken")

fun Application.configureTokenExtraction() {
    intercept(ApplicationCallPipeline.ApplicationPhase.Plugins) {
        val authHeader = call.request.headers["Authorization"]
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.removePrefix("Bearer ").trim()
            call.attributes.put(JwtTokenKey, token)
        }
        proceed()
    }
}
