package com.piedpiper.common

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

fun parseSimpleResponse(bodyText: String?): SimpleResponse {
    if (bodyText.isNullOrBlank()) {
        return SimpleResponse(
            status = HttpStatusCode.InternalServerError.value,
            message = "Empty response from user service"
        )
    }
    log.info { "Received response from user service: $bodyText" }
    return try {
        val jsonElem = Json.parseToJsonElement(bodyText) as? JsonObject
        val remoteStatus = jsonElem?.get("status")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val message = jsonElem?.get("message")?.jsonPrimitive?.contentOrNull ?: ""
        val data: JsonElement? = jsonElem?.get("data")

        SimpleResponse(
            status = remoteStatus ?: HttpStatusCode.InternalServerError.value,
            message = message,
            data = data
        )
    } catch (e: Exception) {
        SimpleResponse(
            status = HttpStatusCode.InternalServerError.value,
            message = "Failed to parse response from user service: ${e.message}"
        )
    }
}