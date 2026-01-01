package com.piedpiper.features.chat.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserInChat(
    val id: String = UUID.randomUUID().toString(),
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