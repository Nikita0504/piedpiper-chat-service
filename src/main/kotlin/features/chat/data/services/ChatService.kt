package com.piedpiper.features.chat.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.common.runWithDefaultOnException
import com.piedpiper.features.chat.data.models.Chat
import com.piedpiper.features.chat.data.models.UserMetadata
import com.piedpiper.features.chat.data.repository.ChatRepository
import com.piedpiper.features.database.Chats
import com.piedpiper.features.database.ChatUsers
import com.piedpiper.features.database.UserInChats
import com.piedpiper.features.user.data.models.User
import com.piedpiper.features.user.data.repository.UserDataRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
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

class ChatService(
    private val database: Database,
    val userRepository: UserDataRepository
): ChatRepository {

    private suspend fun addUsersToChat(
        chatId: UUID,
        participantUserIds: List<String>
    ): Boolean {
        return try {
            newSuspendedTransaction(db = database) {
                for (participantUserId in participantUserIds) {
                    val existing = UserInChats.select {
                        UserInChats.userId eq participantUserId and (UserInChats.chatId eq chatId)
                    }.firstOrNull()
                    
                    if (existing == null) {
                        UserInChats.insert {
                            it[UserInChats.userId] = participantUserId
                            it[UserInChats.chatId] = chatId
                            it[UserInChats.joinedAt] = System.currentTimeMillis()
                        }
                    }
                }
                true
            }
        } catch (e: Exception) {
            false
        }
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
                }
            }
        }
        return metadataList
    }

    override suspend fun getUserChats(requesterUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while receiving user chats: ") {
            val chats = newSuspendedTransaction(db = database) {
                val chatRows = (Chats innerJoin UserInChats)
                    .select {
                        UserInChats.userId eq requesterUserId
                    }
                    .distinctBy { it[Chats.id] }
                
                chatRows.map { chatRow ->
                    val chatId = chatRow[Chats.id].value
                    val users = ChatUsers.select {
                        ChatUsers.chatId eq chatId
                    }.map { userRow ->
                        UserMetadata(
                            userId = userRow[ChatUsers.userId],
                            avatarUrl = userRow[ChatUsers.avatarUrl]
                        )
                    }
                    
                    Chat(
                        id = chatId.toString(),
                        users = users,
                        chatName = chatRow[Chats.chatName],
                        description = chatRow[Chats.description],
                        avatarUrl = chatRow[Chats.avatarUrl]
                    )
                }
            }
            
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
                val existingPrivateChat = newSuspendedTransaction<Chat?>(db = database) {
                    val allChatIds = ChatUsers
                        .slice(ChatUsers.chatId)
                        .select { ChatUsers.chatId.isNotNull() }
                        .distinct()
                        .map { it[ChatUsers.chatId] }
                    
                    allChatIds.mapNotNull { chatId ->
                        val users = ChatUsers.select {
                            ChatUsers.chatId eq chatId
                        }.map { it[ChatUsers.userId] }.toSet()
                        
                        if (users.size == 2 && users == uniqueParticipantIds.toSet()) {
                            val chatRow = Chats.select {
                                Chats.id eq chatId
                            }.first()
                            
                            val userMetadata = ChatUsers.select {
                                ChatUsers.chatId eq chatId
                            }.map { userRow ->
                                UserMetadata(
                                    userId = userRow[ChatUsers.userId],
                                    avatarUrl = userRow[ChatUsers.avatarUrl]
                                )
                            }
                            
                            Chat(
                                id = chatId.toString(),
                                users = userMetadata,
                                chatName = chatRow[Chats.chatName],
                                description = chatRow[Chats.description],
                                avatarUrl = chatRow[Chats.avatarUrl]
                            )
                        } else null
                    }.firstOrNull()
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

            val chatId = UUID.randomUUID()
            val result = newSuspendedTransaction(db = database) {
                try {
                    Chats.insert {
                        it[Chats.id] = chatId
                        it[Chats.chatName] = chatName
                        it[Chats.description] = description
                        it[Chats.avatarUrl] = avatarUrl
                    }
                    
                    for (userMetadata in userMetadataList) {
                        ChatUsers.insert {
                            it[ChatUsers.chatId] = chatId
                            it[ChatUsers.userId] = userMetadata.userId
                            it[ChatUsers.avatarUrl] = userMetadata.avatarUrl
                        }
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }
            
            if (!result) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Couldn't add chat"
                )
            }

            val isSuccessAddUsers = addUsersToChat(chatId, uniqueParticipantIds)
            if (!isSuccessAddUsers) {
                newSuspendedTransaction(db = database) {
                    Chats.deleteWhere { Chats.id eq chatId }
                    ChatUsers.deleteWhere { ChatUsers.chatId eq chatId }
                }
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Couldn't add users to chat"
                )
            }

            val chat = Chat(
                id = chatId.toString(),
                users = userMetadataList,
                chatName = chatName,
                description = description,
                avatarUrl = avatarUrl
            )

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

            val currentChatRow = newSuspendedTransaction(db = database) {
                Chats.select {
                    Chats.id eq chatUuid
                }.firstOrNull()
            }
            
            if (currentChatRow == null) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Chat not found"
                )
            }

            newSuspendedTransaction(db = database) {
                Chats.update({ Chats.id eq chatUuid }) {
                    if (chatName != null) it[Chats.chatName] = chatName
                    if (description != null) it[Chats.description] = description
                    if (avatarUrl != null) it[Chats.avatarUrl] = avatarUrl
                }
            }

            val updatedChat = newSuspendedTransaction(db = database) {
                val chatRow = Chats.select {
                    Chats.id eq chatUuid
                }.first()
                
                val users = ChatUsers.select {
                    ChatUsers.chatId eq chatUuid
                }.map { userRow ->
                    UserMetadata(
                        userId = userRow[ChatUsers.userId],
                        avatarUrl = userRow[ChatUsers.avatarUrl]
                    )
                }
                
                Chat(
                    id = chatUuid.toString(),
                    users = users,
                    chatName = chatRow[Chats.chatName],
                    description = chatRow[Chats.description],
                    avatarUrl = chatRow[Chats.avatarUrl]
                )
            }

            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "The chat has been updated",
                data = Json.encodeToJsonElement(updatedChat)
            )
        }
    }

    override suspend fun addUserInChat(
        chatId: String,
        requesterUserId: String,
        targetUserId: String
    ): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while adding a user to the chat: ") {
            val chatUuid = try {
                UUID.fromString(chatId)
            } catch (e: Exception) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Invalid chat ID format"
                )
            }
            
            val requesterInChat = newSuspendedTransaction(db = database) {
                UserInChats.select {
                    UserInChats.userId eq requesterUserId and (UserInChats.chatId eq chatUuid)
                }.firstOrNull()
            }
            
            if (requesterInChat == null) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Forbidden.value,
                    message = "Requester is not a member of this chat"
                )
            }

            val targetInChat = newSuspendedTransaction(db = database) {
                UserInChats.select {
                    UserInChats.userId eq targetUserId and (UserInChats.chatId eq chatUuid)
                }.firstOrNull()
            }
            
            if (targetInChat != null) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Conflict.value,
                    message = "User is already a member of this chat"
                )
            }

            val chatExists = newSuspendedTransaction(db = database) {
                Chats.select {
                    Chats.id eq chatUuid
                }.firstOrNull() != null
            }
            
            if (!chatExists) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Chat not found"
                )
            }

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

            val success = newSuspendedTransaction(db = database) {
                try {
                    ChatUsers.insert {
                        it[ChatUsers.chatId] = chatUuid
                        it[ChatUsers.userId] = targetUserId
                        it[ChatUsers.avatarUrl] = userData.avatarUrl
                    }
                    
                    UserInChats.insert {
                        it[UserInChats.userId] = targetUserId
                        it[UserInChats.chatId] = chatUuid
                        it[UserInChats.joinedAt] = System.currentTimeMillis()
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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

            val chatExists = newSuspendedTransaction(db = database) {
                Chats.select {
                    Chats.id eq chatUuid
                }.firstOrNull() != null
            }
            
            if (!chatExists) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Chat not found"
                )
            }

            val success = newSuspendedTransaction(db = database) {
                try {
                    UserInChats.deleteWhere {
                        UserInChats.userId eq requesterUserId and (UserInChats.chatId eq chatUuid)
                    }
                    
                    ChatUsers.deleteWhere {
                        ChatUsers.chatId eq chatUuid and (ChatUsers.userId eq requesterUserId)
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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
