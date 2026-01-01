package com.piedpiper.features.chat.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChatMessages(
    val id: String = UUID.randomUUID().toString(),
    var chatId: String,
    var messages: List<Message> = listOf(),
){
    fun addMessages(message: Message): ChatMessages {
        return copy(messages = messages + message)
    }


    fun updateMessages(message: Message): ChatMessages {
        return copy(messages = messages.map {
            if(it.id == message.id) message else it
        })
    }

    fun deleteMessages(messageId: String): ChatMessages {
        return copy(messages = messages.filter { it.id != messageId })
    }
}
