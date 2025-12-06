package com.piedpiper.features.chat.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class ChatMessages(
    @BsonId
    val id: String = ObjectId().toString(),
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
