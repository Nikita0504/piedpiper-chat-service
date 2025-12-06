package features.chat.data.socket

import com.piedpiper.common.SimpleResponse
import com.piedpiper.features.chat.data.models.Chat
import com.piedpiper.features.chat.data.models.Message
import com.piedpiper.features.chat.data.models.UserMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
sealed class SocketMessage {

    @Serializable
    @SerialName("new_message")
    data class NewMessage(val chatId: String, val message: Message) : SocketMessage()

    @Serializable
    @SerialName("update_message")
    data class UpdateMessage(val chatId: String, val message: Message) : SocketMessage()

    @Serializable
    @SerialName("delete_message")
    data class DeleteMessage(val chatId: String, val messageId: String) : SocketMessage()

    @Serializable
    @SerialName("new_chat")
    data class NewChat(val chat: Chat) : SocketMessage()

    @Serializable
    @SerialName("chat_updated")
    data class ChatUpdated(val chat: Chat) : SocketMessage()

    @Serializable
    @SerialName("user_added_to_chat")
    data class UserAddedToChat(val chatId: String, val userId: String, val userMetadata: UserMetadata) : SocketMessage()

    @Serializable
    @SerialName("user_left_chat")
    data class UserLeftChat(val chatId: String, val userId: String, val isPublic: Boolean = true) : SocketMessage()

    @Serializable
    @SerialName("subscribe_to_messages")
    data class SubscribeToMessages(val chatId: String) : SocketMessage()

    @Serializable
    @SerialName("unsubscribe_from_messages")
    data class UnsubscribeFromMessages(val chatId: String) : SocketMessage()

    // Ошибки
    @Serializable
    @SerialName("error_message")
    data class ErrorMessage(val simpleResponse: SimpleResponse) : SocketMessage()
}

