package com.piedpiper.features.user.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.features.user.data.models.User
import com.piedpiper.features.user.data.repository.UserDataRepository
import com.piedpiper.common.log
import com.piedpiper.common.parseSimpleResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class UserService(
    private val client: HttpClient,
    private val userServiceBaseUrl: String,
) : UserDataRepository {

    override suspend fun getUserByUsername(username: String): SimpleResponse {
        return try {
            val url = Url("$userServiceBaseUrl/users")
            val response: HttpResponse = client.get(url) {
                parameter("username", username)
                accept(ContentType.Application.Json)
            }
            val bodyText = response.bodyAsText()
            parseSimpleResponse(bodyText)
        } catch (e: Exception) {
            SimpleResponse(
                status = HttpStatusCode.InternalServerError.value,
                message = "Failed to fetch user by username from remote service: ${e.message}"
            )
        }
    }

    override suspend fun getAllUsers(): SimpleResponse {
        return try {
            val url = Url("$userServiceBaseUrl/users")
            val response: HttpResponse = client.get(url) {
                accept(ContentType.Application.Json)
            }
            val bodyText = response.bodyAsText()
            parseSimpleResponse(bodyText)
        } catch (e: Exception) {
            SimpleResponse(
                status = HttpStatusCode.InternalServerError.value,
                message = "Failed to fetch users from remote service: ${e.message}"
            )
        }
    }

    override suspend fun getUserById(id: String): SimpleResponse {
        return try {
            val url = Url("$userServiceBaseUrl/users/$id")
            val response: HttpResponse = client.get(url) {
                accept(ContentType.Application.Json)
            }
            val bodyText = response.bodyAsText()
            parseSimpleResponse(bodyText)
        } catch (e: Exception) {
            SimpleResponse(
                status = HttpStatusCode.InternalServerError.value,
                message = "Failed to fetch user by id from remote service: ${e.message}"
            )
        }
    }

    override suspend fun updateUser(user: User): SimpleResponse {
        return try {
            val url = Url("$userServiceBaseUrl/users/${user.id}")
            val payload = Json.encodeToString(user)
            val response: HttpResponse = client.put(url) {
                contentType(ContentType.Application.Json)
                setBody(payload)
                accept(ContentType.Application.Json)
            }
            val bodyText = response.bodyAsText()
            parseSimpleResponse(bodyText)
        } catch (e: Exception) {
            SimpleResponse(
                status = HttpStatusCode.InternalServerError.value,
                message = "Failed to update user on remote service: ${e.message}"
            )
        }
    }

    override suspend fun deleteUserById(id: String): SimpleResponse {
        return try {
            val url = Url("$userServiceBaseUrl/users/$id")
            val response: HttpResponse = client.delete(url) {
                accept(ContentType.Application.Json)
            }
            val bodyText = response.bodyAsText()
            parseSimpleResponse(bodyText)
        } catch (e: Exception) {
            SimpleResponse(
                status = HttpStatusCode.InternalServerError.value,
                message = "Failed to delete user on remote service: ${e.message}",
            )
        }
    }

}