package com.piedpiper.features.chat.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
enum class MessageType {
    TEXT,
    WEBRTC_SIGNAL
}

@Serializable
data class FileMetadata(
    val fileName: String,
    val fileExtension: String,
    val fileSize: Long,
    val extraInformation: String,
)

@Serializable
data class MessagesResponse(
    val messages: List<Message>,
    val hasMore: Boolean
)

@Serializable
data class Message(
    @BsonId
    val id: String = ObjectId().toString(),
    val sender: String,
    val payload: String,
    val timestamp: Long,
    val type: MessageType = MessageType.TEXT,
    val replyText: String? = null,
    val fileMetadata: FileMetadata? = null,
    val isUpdateMessage: Boolean = false,
)
