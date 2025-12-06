package com.piedpiper.common

import io.ktor.http.HttpStatusCode

suspend fun runWithDefaultOnException(errorMessage: String, block: suspend () -> SimpleResponse): SimpleResponse {
    return try {
        block()
    } catch (e: Exception) {
        SimpleResponse(
            status = HttpStatusCode.BadRequest.value,
            message = errorMessage + e.message,
        )
    }
}