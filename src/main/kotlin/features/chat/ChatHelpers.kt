package com.piedpiper.features.chat

import com.piedpiper.features.database.UserInChats
import com.piedpiper.features.token.data.repository.TokenRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

suspend fun extractRequesterUserId(
    tokenRepository: TokenRepository,
    accessToken: String?
): String? {
    if (accessToken == null) return null
    
    val tokenResponse = tokenRepository.validateAccessToken(accessToken)
    val userIDJsonElement = tokenResponse.data
    
    if (userIDJsonElement == null || userIDJsonElement is JsonNull) {
        return null
    }
    
    return try {
        Json.decodeFromJsonElement<String>(userIDJsonElement)
    } catch (e: Exception) {
        null
    }
}

suspend fun isUserMemberOfChat(
    database: Database,
    userId: String,
    chatId: String
): Boolean {
    val chatUuid = try {
        UUID.fromString(chatId)
    } catch (e: Exception) {
        return false
    }
    
    return newSuspendedTransaction(db = database) {
        UserInChats.select {
            UserInChats.userId eq userId and (UserInChats.chatId eq chatUuid)
        }.firstOrNull() != null
    }
}

