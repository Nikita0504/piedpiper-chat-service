package com.piedpiper.features.chat

import com.piedpiper.features.chat.data.models.UserInChats
import com.piedpiper.features.token.data.repository.TokenRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq

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

/**
 * Проверяет, является ли пользователь участником чата
 * @return true если пользователь является участником, false иначе
 */
suspend fun isUserMemberOfChat(
    dataBase: CoroutineDatabase,
    userId: String,
    chatId: String
): Boolean {
    val userInChatsCollection = dataBase.getCollection<UserInChats>()
    val userChats = userInChatsCollection.findOne(UserInChats::userId eq userId) ?: return false
    return userChats.getUserInChatByChatId(chatId) != null
}

