package com.piedpiper.features.chat.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.common.runWithDefaultOnException
import com.piedpiper.features.chat.data.models.Message
import com.piedpiper.features.chat.data.models.MessageType
import com.piedpiper.features.chat.data.models.MessagesResponse
import com.piedpiper.features.chat.data.repository.MessageRepository
import com.piedpiper.features.database.Messages
import com.piedpiper.features.database.UserInChats
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

class MessageService(
    private val database: Database,
): MessageRepository {

    override suspend fun getMessages(
        chatId: String,
        afterTimestamp: Long?,
        limit: Int
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while receiving the messages: ") {
            val chatUuid = try {
                UUID.fromString(chatId)
            } catch (e: Exception) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Invalid chat ID format"
                )
            }
            
            val messages = newSuspendedTransaction(db = database) {
                val query = Messages.select {
                    Messages.chatId eq chatUuid
                }
                
                val allMessages = query.map { row ->
                    Message(
                        id = row[Messages.id].value.toString(),
                        sender = row[Messages.sender],
                        payload = row[Messages.payload],
                        timestamp = row[Messages.timestamp],
                        type = MessageType.valueOf(row[Messages.type]),
                        replyText = row[Messages.replyText],
                        fileMetadata = if (row[Messages.fileName] != null) {
                            com.piedpiper.features.chat.data.models.FileMetadata(
                                fileName = row[Messages.fileName]!!,
                                fileExtension = row[Messages.fileExtension]!!,
                                fileSize = row[Messages.fileSize]!!,
                                extraInformation = row[Messages.extraInformation] ?: ""
                            )
                        } else null,
                        isUpdateMessage = row[Messages.isUpdateMessage]
                    )
                }
                
                val filtered = if (afterTimestamp != null) {
                    allMessages.filter { it.timestamp > afterTimestamp }
                } else {
                    allMessages
                }
                
                filtered.sortedBy { it.timestamp }.take(limit)
            }
            
            val totalCount = newSuspendedTransaction(db = database) {
                val query = Messages.select {
                    Messages.chatId eq chatUuid
                }
                
                val allMessages = query.map { it[Messages.timestamp] }
                val filtered = if (afterTimestamp != null) {
                    allMessages.filter { it > afterTimestamp }
                } else {
                    allMessages
                }
                
                filtered.size
            }
            
            val hasMore = totalCount > limit

            val responseData = MessagesResponse(
                messages = messages,
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
            val chatUuid = try {
                UUID.fromString(chatId)
            } catch (e: Exception) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Invalid chat ID format"
                )
            }
            
            val userInChat = newSuspendedTransaction(db = database) {
                UserInChats.select {
                    UserInChats.userId eq requesterUserId and (UserInChats.chatId eq chatUuid)
                }.firstOrNull()
            }
            
            if (userInChat == null) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "User is not a member of this chat"
                )
            }

            val messageId = UUID.randomUUID()
            val success = newSuspendedTransaction(db = database) {
                try {
                    Messages.insert {
                        it[Messages.id] = messageId
                        it[Messages.chatId] = chatUuid
                        it[Messages.sender] = message.sender
                        it[Messages.payload] = message.payload
                        it[Messages.timestamp] = message.timestamp
                        it[Messages.type] = message.type.name
                        it[Messages.replyText] = message.replyText
                        it[Messages.fileName] = message.fileMetadata?.fileName
                        it[Messages.fileExtension] = message.fileMetadata?.fileExtension
                        it[Messages.fileSize] = message.fileMetadata?.fileSize
                        it[Messages.extraInformation] = message.fileMetadata?.extraInformation
                        it[Messages.isUpdateMessage] = message.isUpdateMessage
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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
            val chatUuid = try {
                UUID.fromString(chatId)
            } catch (e: Exception) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Invalid chat ID format"
                )
            }
            
            val messageId = try {
                UUID.fromString(message.id)
            } catch (e: Exception) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Invalid message ID format"
                )
            }
            
            val messageExists = newSuspendedTransaction(db = database) {
                Messages.select {
                    Messages.id eq messageId and (Messages.chatId eq chatUuid)
                }.firstOrNull() != null
            }
            
            if (!messageExists) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Message not found"
                )
            }

            val success = newSuspendedTransaction(db = database) {
                try {
                    Messages.update({
                        Messages.id eq messageId and (Messages.chatId eq chatUuid)
                    }) {
                        it[Messages.payload] = message.payload
                        it[Messages.replyText] = message.replyText
                        it[Messages.fileName] = message.fileMetadata?.fileName
                        it[Messages.fileExtension] = message.fileMetadata?.fileExtension
                        it[Messages.fileSize] = message.fileMetadata?.fileSize
                        it[Messages.extraInformation] = message.fileMetadata?.extraInformation
                        it[Messages.isUpdateMessage] = message.isUpdateMessage
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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
            val chatUuid = try {
                UUID.fromString(chatId)
            } catch (e: Exception) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Invalid chat ID format"
                )
            }
            
            val msgId = try {
                UUID.fromString(messageId)
            } catch (e: Exception) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Invalid message ID format"
                )
            }
            
            val success = newSuspendedTransaction(db = database) {
                try {
                    Messages.deleteWhere {
                        Messages.id eq msgId and (Messages.chatId eq chatUuid)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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
