package com.piedpiper.features.chat.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.common.runWithDefaultOnException
import com.piedpiper.features.chat.data.models.ChatMessages
import com.piedpiper.features.chat.data.models.Message
import com.piedpiper.features.chat.data.models.MessagesResponse
import com.piedpiper.features.chat.data.models.UserInChats
import com.piedpiper.features.chat.data.repository.MessageRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq

class MessageService(
    dataBase: CoroutineDatabase,
): MessageRepository {

    private val userInChatsCollection = dataBase.getCollection<UserInChats>()
    private val chatMessagesCollection = dataBase.getCollection<ChatMessages>()

    override suspend fun getMessages(
        chatId: String,
        afterTimestamp: Long?,
        limit: Int
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while receiving the messages: ") {
            val chatMessages = chatMessagesCollection.findOne(ChatMessages::chatId eq chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't receive chat messages"
                )

            val filteredMessages = chatMessages.messages
                .filter { afterTimestamp == null || it.timestamp > afterTimestamp }
                .sortedBy { it.timestamp }
                .take(limit)

            val hasMore = chatMessages.messages.count { afterTimestamp == null || it.timestamp > afterTimestamp } > limit

            val responseData = MessagesResponse(
                messages = filteredMessages,
                hasMore = hasMore
            )

            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "Messages received successfully",
                data = Json.encodeToJsonElement(responseData)
            )
        }
    }

    override suspend fun sendMessage(
        chatId: String,
        requesterUserId: String,
        message: Message
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while sending the message: ") {
            val userInChats = userInChatsCollection.findOne(UserInChats::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "User not found"
                )

            val userInChat = userInChats.getUserInChatByChatId(chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "User is not a member of this chat"
                )

            val chatMessages = chatMessagesCollection.findOne(ChatMessages::chatId eq chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't receive chat messages"
                )

            // Добавляем сообщение
            val updatedChatMessages = chatMessages.addMessages(message)
            val isSuccess = chatMessagesCollection.updateOne(
                ChatMessages::chatId eq chatId,
                updatedChatMessages
            ).wasAcknowledged()

            if (isSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "The message was sent successfully"
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "The message was not sent"
                )
            }
        }
    }

    override suspend fun updateMessage(
        chatId: String,
        message: Message
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while updating the message:") {
            val chatMessages = chatMessagesCollection.findOne(ChatMessages::chatId eq chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't receive chat messages"
                )

            val updatedChatMessages = chatMessages.updateMessages(message)
            val isSuccess = chatMessagesCollection.updateOne(
                ChatMessages::chatId eq chatId,
                updatedChatMessages
            ).wasAcknowledged()

            if (isSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "The message has been successfully updated"
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "The message has not been updated"
                )
            }
        }
    }

    override suspend fun deleteMessage(
        chatId: String,
        messageId: String
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while deleting the message:") {
            val chatMessages = chatMessagesCollection.findOne(ChatMessages::chatId eq chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't receive chat messages"
                )

            val updatedChatMessages = chatMessages.deleteMessages(messageId)
            val isSuccess = chatMessagesCollection.updateOne(
                ChatMessages::chatId eq chatId,
                updatedChatMessages
            ).wasAcknowledged()

            if (isSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "The message was successfully deleted"
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "The message has not been deleted"
                )
            }
        }
    }
}
