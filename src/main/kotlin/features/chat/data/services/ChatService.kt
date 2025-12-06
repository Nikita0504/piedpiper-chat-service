package com.piedpiper.features.chat.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.common.runWithDefaultOnException
import com.piedpiper.features.chat.data.models.Chat
import com.piedpiper.features.chat.data.models.ChatMessages
import com.piedpiper.features.chat.data.models.UserInChat
import com.piedpiper.features.chat.data.models.UserInChats
import com.piedpiper.features.chat.data.models.UserMetadata
import com.piedpiper.features.chat.data.repository.ChatRepository
import com.piedpiper.features.user.data.models.User
import com.piedpiper.features.user.data.repository.UserDataRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.bson.types.ObjectId
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq
import org.litote.kmongo.setValue
import org.litote.kmongo.`in`

class ChatService(
    dataBase: CoroutineDatabase,
    val userRepository: UserDataRepository
): ChatRepository {

    private val chatCollection = dataBase.getCollection<Chat>()
    private val userInChatsCollection = dataBase.getCollection<UserInChats>()
    private val chatMessagesCollection = dataBase.getCollection<ChatMessages>()

    private suspend fun addUsersToChat(
        chatId: String,
        participantUserIds: List<String>
    ): Boolean {
        for (participantUserId in participantUserIds) {
            val userChats = userInChatsCollection.findOne(UserInChats::userId eq participantUserId)
            val userInChat = UserInChat(chatId = chatId)

            if (userChats == null) {
                val newUserChats = UserInChats(userId = participantUserId, chats = listOf(userInChat))
                if (!userInChatsCollection.insertOne(newUserChats).wasAcknowledged()) return false
            } else {
                val updatedChats = userChats.chats + userInChat
                if (!userInChatsCollection.updateOne(
                        UserInChats::userId eq participantUserId,
                        setValue(UserInChats::chats, updatedChats)
                    ).wasAcknowledged()) return false
            }
        }
        return true
    }

    private suspend fun getUserMetadataList(userIds: List<String>): List<UserMetadata> {
        val metadataList = mutableListOf<UserMetadata>()
        for (userId in userIds) {
            val userResponse = userRepository.getUserById(userId)
            val userDataJsonEl = userResponse.data
            
            if (userDataJsonEl != null && userDataJsonEl !is JsonNull) {
                try {
                    val user: User = Json.decodeFromJsonElement(userDataJsonEl)
                    metadataList.add(
                        UserMetadata(
                            userId = user.id,
                            avatarUrl = user.avatarUrl
                        )
                    )
                } catch (e: Exception) {
                    // Пропускаем пользователя, если не удалось получить данные
                }
            }
        }
        return metadataList
    }

    override suspend fun getUserChats(requesterUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while receiving user chats: ") {
            val userChats = userInChatsCollection.findOne(UserInChats::userId eq requesterUserId)
            val chatIds: List<String> = userChats?.chats?.map { it.chatId } ?: emptyList()
            val chats = chatCollection.find(Chat::id `in` chatIds).toList()
            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "The user's chats were successfully received",
                data = Json.encodeToJsonElement(chats)
            )
        }
    }

    override suspend fun createChat(
        participantUserIds: List<String>,
        requesterUserId: String,
        chatName: String?,
        description: String?,
        avatarUrl: String?
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred during chat creation: ") {
            if (!participantUserIds.contains(requesterUserId)) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Requester must be included in participant list"
                )
            }

            val uniqueParticipantIds = participantUserIds.distinct()
            if (uniqueParticipantIds.size < 2) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Chat must have at least 2 participants"
                )
            }

            if (uniqueParticipantIds.size == 2) {
                val existingChats = chatCollection.find().toList()
                val existingPrivateChat = existingChats.find { chat ->
                    chat.users.size == 2 &&
                    chat.users.map { it.userId }.toSet() == uniqueParticipantIds.toSet()
                }
                
                if (existingPrivateChat != null) {
                    return@runWithDefaultOnException SimpleResponse(
                        status = HttpStatusCode.Conflict.value,
                        message = "Private chat already exists",
                        data = Json.encodeToJsonElement(existingPrivateChat)
                    )
                }
            }

            val userMetadataList = getUserMetadataList(uniqueParticipantIds)
            if (userMetadataList.size != uniqueParticipantIds.size) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Could not get metadata for all participants"
                )
            }

            val chatId = ObjectId().toString()
            val chat = Chat(
                id = chatId,
                users = userMetadataList,
                chatName = chatName,
                description = description,
                avatarUrl = avatarUrl
            )

            val isSuccessAddChat = chatCollection.insertOne(chat).wasAcknowledged()
            if (!isSuccessAddChat) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Couldn't add chat"
                )
            }

            val chatMessages = ChatMessages(chatId = chatId, messages = listOf())
            val isSuccessAddMessages = chatMessagesCollection.insertOne(chatMessages).wasAcknowledged()
            if (!isSuccessAddMessages) {
                chatCollection.deleteOne(Chat::id eq chatId)
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Couldn't add chat messages record"
                )
            }

            val isSuccessAddUsers = addUsersToChat(chatId, uniqueParticipantIds)
            if (!isSuccessAddUsers) {
                chatCollection.deleteOne(Chat::id eq chatId)
                chatMessagesCollection.deleteOne(ChatMessages::chatId eq chatId)
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Couldn't add users to chat"
                )
            }

            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "The chat was created successfully",
                data = Json.encodeToJsonElement(chat)
            )
        }
    }

    override suspend fun updateChat(
        chatId: String,
        requesterUserId: String,
        chatName: String?,
        description: String?,
        avatarUrl: String?
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while updating the chat: ") {
            val userChats = userInChatsCollection.findOne(UserInChats::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "User is not part of any chat"
                )
            
            val userChat = userChats.chats.find { it.chatId == chatId }
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "User is not a member of this chat"
                )

            val currentChat = chatCollection.findOne(Chat::id eq chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Chat not found"
                )

            val updatedChat = currentChat.copy(
                chatName = chatName ?: currentChat.chatName,
                description = description ?: currentChat.description,
                avatarUrl = avatarUrl ?: currentChat.avatarUrl
            )

            val isSuccess = chatCollection.updateOne(Chat::id eq chatId, updatedChat).wasAcknowledged()

            if (isSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "The chat has been updated",
                    data = Json.encodeToJsonElement(updatedChat)
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't update chat"
                )
            }
        }
    }

    override suspend fun addUserInChat(
        chatId: String,
        requesterUserId: String,
        targetUserId: String
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while adding a user to the chat: ") {
            val requesterChats = userInChatsCollection.findOne(UserInChats::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "Requester not found"
                )

            val requesterInChat = requesterChats.getUserInChatByChatId(chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "Requester is not a member of this chat"
                )

            val targetChats = userInChatsCollection.findOne(UserInChats::userId eq targetUserId)
            val targetInChat = targetChats?.getUserInChatByChatId(chatId)
            
            if (targetInChat != null) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Conflict.value,
                    message = "User is already a member of this chat"
                )
            }

            val chat = chatCollection.findOne(Chat::id eq chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Chat not found"
                )

            val userDataResponse = userRepository.getUserById(targetUserId)
            val userDataJsonEl = userDataResponse.data
            val userData: User? = if (userDataJsonEl != null && userDataJsonEl !is JsonNull) {
                try {
                    Json.decodeFromJsonElement(userDataJsonEl)
                } catch (e: Exception) {
                    null
                }
            } else null

            if (userData == null) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't get user data"
                )
            }

            val newUserMetadata = UserMetadata(
                userId = targetUserId,
                avatarUrl = userData.avatarUrl
            )
            val updatedChat = chat.addUserMetadata(newUserMetadata)

            val targetUserInChat = UserInChat(chatId = chatId)
            val updatedTargetChats = if (targetChats == null) {
                UserInChats(userId = targetUserId, chats = listOf(targetUserInChat))
            } else {
                targetChats.addChat(targetUserInChat)
            }

            val isUserInChatUpdateSuccess = userInChatsCollection.updateOne(
                UserInChats::userId eq targetUserId,
                updatedTargetChats
            ).wasAcknowledged()
            
            val isChatUpdateSuccess = chatCollection.updateOne(
                Chat::id eq chatId,
                updatedChat
            ).wasAcknowledged()

            if (isUserInChatUpdateSuccess && isChatUpdateSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "The user has been successfully added to the chat",
                    data = Json.encodeToJsonElement(newUserMetadata)
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't add user to chat"
                )
            }
        }
    }

    override suspend fun leaveChat(
        chatId: String,
        requesterUserId: String
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while leaving the chat: ") {
            val userChats = userInChatsCollection.findOne(UserInChats::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "User not found"
                )

            val userInChat = userChats.getUserInChatByChatId(chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "User is not a member of this chat"
                )

            val chat = chatCollection.findOne(Chat::id eq chatId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Chat not found"
                )

            val updatedUserChats = userChats.removeChatById(chatId)
            val isUserInChatUpdateSuccess = userInChatsCollection.updateOne(
                UserInChats::userId eq requesterUserId,
                updatedUserChats
            ).wasAcknowledged()

            val updatedChat = chat.copy(
                users = chat.users.filter { it.userId != requesterUserId }
            )
            val isChatUpdateSuccess = chatCollection.updateOne(
                Chat::id eq chatId,
                updatedChat
            ).wasAcknowledged()

            if (isUserInChatUpdateSuccess && isChatUpdateSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "User has successfully left the chat"
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Couldn't leave chat"
                )
            }
        }
    }
}
