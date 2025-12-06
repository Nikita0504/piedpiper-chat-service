package com.piedpiper.features.chat.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class UserInChat(
    @BsonId
    val id: String = ObjectId().toString(),
    val chatId: String,
    val joinedAt: Long = System.currentTimeMillis()
)

@Serializable
data class UserInChats(
    val userId: String,
    var chats: List<UserInChat> = listOf()
) {
    fun getUserInChatByChatId(chatId: String): UserInChat? {
        return chats.find { it.chatId == chatId }
    }

    fun addChat(userInChat: UserInChat): UserInChats {
        return copy(chats = chats + userInChat)
    }

    fun removeChatById(chatId: String): UserInChats {
        return copy(chats = chats.filterNot { it.chatId == chatId })
    }
}