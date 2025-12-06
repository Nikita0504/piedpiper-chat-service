package com.piedpiper.features.token.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.common.log
import com.piedpiper.common.parseSimpleResponse
import com.piedpiper.features.token.data.models.AccessToken
import com.piedpiper.features.token.data.repository.TokenRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

class TokenService (
    private val client: HttpClient,
    private val userServiceBaseUrl: String,
) : TokenRepository{

    override suspend fun validateAccessToken(accessToken: String): SimpleResponse {
        return try {
            val url = Url("$userServiceBaseUrl/validate-access-token")
            val response: HttpResponse = client.post(url){
                contentType(ContentType.Application.Json)
                setBody(AccessToken(accessToken = accessToken))
                accept(ContentType.Application.Json)
            }

            val bodyText = response.bodyAsText()

            log.info(bodyText)

            parseSimpleResponse(bodyText)
        }catch (e: Exception){
            SimpleResponse(status = HttpStatusCode.BadRequest.value, message = "An error occurred during the validation of the user's token: ${e.message}")
        }
    }

}