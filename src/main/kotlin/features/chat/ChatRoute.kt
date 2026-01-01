package com.piedpiper.features.chat

import com.piedpiper.common.SimpleResponse
import com.piedpiper.features.chat.data.models.Chat
import com.piedpiper.features.chat.data.repository.ChatRepository
import com.piedpiper.features.chat.data.repository.MessageRepository
import com.piedpiper.features.chat.data.socket.ClientSession
import com.piedpiper.features.chat.data.socket.RoomManager
import features.chat.data.socket.SocketMessage
import com.piedpiper.features.token.data.repository.TokenRepository
import com.piedpiper.plugins.JwtTokenKey
import io.ktor.server.routing.Route
import org.koin.ktor.ext.get
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.builtins.ListSerializer
import com.piedpiper.features.chat.isUserMemberOfChat
import features.chat.request.CreateChatRequest
import features.chat.request.UpdateChatRequest

fun Route.chatRoute() {
    val chatRepository: ChatRepository = get<ChatRepository>()
    val tokenRepository: TokenRepository = get<TokenRepository>()
    val messageRepository: MessageRepository = get<MessageRepository>()
    val database = get<org.jetbrains.exposed.sql.Database>()

    webSocket("/ws/chats") {
        val accessToken = call.attributes.getOrNull(JwtTokenKey) ?: return@webSocket close(
            CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No token")
        )
        
        val requesterUserId = extractRequesterUserId(tokenRepository, accessToken)
            ?: return@webSocket close(
                CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token")
            )

        val clientSession = ClientSession(this, requesterUserId)
        
        RoomManager.subscribeUserToAllChats(requesterUserId, clientSession)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val socketMessage = Json.decodeFromString<SocketMessage>(text)
                        when (socketMessage) {
                            is SocketMessage.UserLeftChat -> {
                                val getUserChatsResponse = chatRepository.getUserChats(requesterUserId)
                                val chatBeforeLeave: Chat? = if (getUserChatsResponse.status == HttpStatusCode.OK.value) {
                                    try {
                                        val chatsJson = getUserChatsResponse.data
                                        if (chatsJson != null) {
                                            val chats = Json.decodeFromJsonElement(ListSerializer(Chat.serializer()), chatsJson)
                                            chats.find { it.id == socketMessage.chatId }
                                        } else null
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else null
                                
                                val response = chatRepository.leaveChat(
                                    socketMessage.chatId,
                                    requesterUserId
                                )
                                
                                if (response.status == HttpStatusCode.OK.value) {
                                    if (socketMessage.isPublic && chatBeforeLeave != null) {
                                        val participantUserIds = chatBeforeLeave.users.map { it.userId }
                                        RoomManager.broadcastToChatParticipants(
                                            socketMessage.chatId,
                                            participantUserIds,
                                            socketMessage
                                        )
                                    } else if (!socketMessage.isPublic) {
                                        clientSession.trySendRaw(Json.encodeToString(SocketMessage.serializer(), socketMessage))
                                    }
                                } else {
                                    val errorMsg = SocketMessage.ErrorMessage(
                                        simpleResponse = SimpleResponse(response.status, response.message)
                                    )
                                    clientSession.trySendRaw(Json.encodeToString(SocketMessage.serializer(), errorMsg))
                                }
                            }
                            else -> {
                                val errorMsg = SocketMessage.ErrorMessage(
                                    simpleResponse = SimpleResponse(
                                        HttpStatusCode.BadRequest.value,
                                        "Unsupported message type for this socket"
                                    )
                                )
                                clientSession.trySendRaw(Json.encodeToString(SocketMessage.serializer(), errorMsg))
                            }
                        }
                    } catch (e: Exception) {
                        val errorMsg = SocketMessage.ErrorMessage(
                            simpleResponse = SimpleResponse(
                                HttpStatusCode.BadRequest.value,
                                "Invalid message format: ${e.message}"
                            )
                        )
                        val errorJson = Json.encodeToString(SocketMessage.serializer(), errorMsg)
                        clientSession.trySendRaw(errorJson)
                    }
                }
            }
        } finally {
            RoomManager.unsubscribeUserFromAllChats(requesterUserId, clientSession)
        }
    }

    webSocket("/ws/messages") {
        val accessToken = call.attributes.getOrNull(JwtTokenKey) ?: return@webSocket close(
            CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No token")
        )
        
        val requesterUserId = extractRequesterUserId(tokenRepository, accessToken)
            ?: return@webSocket close(
                CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token")
            )

        val clientSession = ClientSession(this, requesterUserId)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val socketMessage = Json.decodeFromString<SocketMessage>(text)
                        val response = when (socketMessage) {
                            is SocketMessage.NewMessage -> {
                                messageRepository.sendMessage(
                                    socketMessage.chatId,
                                    requesterUserId,
                                    socketMessage.message
                                )
                            }
                            is SocketMessage.UpdateMessage -> {
                                messageRepository.updateMessage(
                                    socketMessage.chatId,
                                    socketMessage.message
                                )
                            }
                            is SocketMessage.DeleteMessage -> {
                                messageRepository.deleteMessage(
                                    socketMessage.chatId,
                                    socketMessage.messageId
                                )
                            }
                            is SocketMessage.SubscribeToMessages -> {
                                if (isUserMemberOfChat(database, requesterUserId, socketMessage.chatId)) {
                                    RoomManager.subscribeToMessages(socketMessage.chatId, clientSession)
                                    SimpleResponse(
                                        status = HttpStatusCode.OK.value,
                                        message = "Subscribed to messages"
                                    )
                                } else {
                                    SimpleResponse(
                                        status = HttpStatusCode.Forbidden.value,
                                        message = "User is not a member of this chat"
                                    )
                                }
                            }
                            is SocketMessage.UnsubscribeFromMessages -> {
                                RoomManager.unsubscribeFromMessages(socketMessage.chatId, clientSession)
                                SimpleResponse(
                                    status = HttpStatusCode.OK.value,
                                    message = "Unsubscribed from messages"
                                )
                            }
                            else -> null
                        }

                        if (response == null) {
                            val errorMsg = SocketMessage.ErrorMessage(
                                simpleResponse = SimpleResponse(
                                    HttpStatusCode.BadRequest.value,
                                    "Unknown message type"
                                )
                            )
                            val errorJson = Json.encodeToString(SocketMessage.serializer(), errorMsg)
                            clientSession.trySendRaw(errorJson)
                        } else if (response.status == HttpStatusCode.OK.value) {
                            // Успешная операция - отправляем событие подписанным
                            when (socketMessage) {
                                is SocketMessage.NewMessage -> {
                                    RoomManager.broadcastToSubscribed(
                                        socketMessage.chatId,
                                        socketMessage
                                    )
                                }
                                is SocketMessage.UpdateMessage -> {
                                    RoomManager.broadcastToSubscribed(
                                        socketMessage.chatId,
                                        socketMessage
                                    )
                                }
                                is SocketMessage.DeleteMessage -> {
                                    RoomManager.broadcastToSubscribed(
                                        socketMessage.chatId,
                                        socketMessage
                                    )
                                }
                                else -> {}
                            }
                        } else {
                            val errorMsg = SocketMessage.ErrorMessage(
                                simpleResponse = SimpleResponse(response.status, response.message)
                            )
                            val errorJson = Json.encodeToString(SocketMessage.serializer(), errorMsg)
                            clientSession.trySendRaw(errorJson)
                        }
                    } catch (e: Exception) {
                        val errorMsg = SocketMessage.ErrorMessage(
                            simpleResponse = SimpleResponse(
                                HttpStatusCode.BadRequest.value,
                                "Invalid message format: ${e.message}"
                            )
                        )
                        val errorJson = Json.encodeToString(SocketMessage.serializer(), errorMsg)
                        clientSession.trySendRaw(errorJson)
                    }
                }
            }
        } finally {
            clientSession.subscribedChatIds.toList().forEach { chatId ->
                RoomManager.unsubscribeFromMessages(chatId, clientSession)
            }
        }
    }

    get("/") {
        val accessToken = call.attributes.getOrNull(JwtTokenKey)
            ?: return@get call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "No token")
            )

        val requesterUserId = extractRequesterUserId(tokenRepository, accessToken)
            ?: return@get call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "Invalid token")
            )

        val response = chatRepository.getUserChats(requesterUserId)
        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    get("/messages/{chatId}") {
        val chatId = call.parameters["chatId"]
            ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                SimpleResponse(HttpStatusCode.BadRequest.value, "chatId missing")
            )
        
        val afterTimestamp = call.request.queryParameters["afterTimestamp"]?.toLongOrNull()
        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

        val response = messageRepository.getMessages(chatId, afterTimestamp, limit)
        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    post("/create") {
        val accessToken = call.attributes.getOrNull(JwtTokenKey)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "No token")
            )

        val requesterUserId = extractRequesterUserId(tokenRepository, accessToken)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "Invalid token")
            )

        val request = call.receiveNullable<CreateChatRequest>()
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                SimpleResponse(HttpStatusCode.BadRequest.value, "Invalid data format")
            )

        val response = chatRepository.createChat(
            participantUserIds = request.participantUserIds,
            requesterUserId = requesterUserId,
            chatName = request.chatName,
            description = request.description,
            avatarUrl = request.avatarUrl
        )

        if (response.status == HttpStatusCode.OK.value) {
            val chat: Chat? = try {
                val chatJson = response.data
                if (chatJson != null) {
                    Json.decodeFromJsonElement(Chat.serializer(), chatJson)
                } else null
            } catch (e: Exception) {
                null
            }
            
            if (chat != null) {
                val newChatEvent = SocketMessage.NewChat(chat)
                RoomManager.broadcastToChatParticipants(
                    chat.id,
                    request.participantUserIds,
                    newChatEvent
                )
            }
        }

        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    post("/{chatId}/update") {
        val chatId = call.parameters["chatId"]
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                SimpleResponse(HttpStatusCode.BadRequest.value, "chatId missing")
            )

        val accessToken = call.attributes.getOrNull(JwtTokenKey)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "No token")
            )

        val requesterUserId = extractRequesterUserId(tokenRepository, accessToken)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "Invalid token")
            )

        val request = call.receiveNullable<UpdateChatRequest>()
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                SimpleResponse(HttpStatusCode.BadRequest.value, "Invalid data format")
            )

        val response = chatRepository.updateChat(
            chatId = chatId,
            requesterUserId = requesterUserId,
            chatName = request.chatName,
            description = request.description,
            avatarUrl = request.avatarUrl
        )

        if (response.status == HttpStatusCode.OK.value) {
            val chat: Chat? = try {
                val chatJson = response.data
                if (chatJson != null) {
                    Json.decodeFromJsonElement(Chat.serializer(), chatJson)
                } else null
            } catch (e: Exception) {
                null
            }
            
            if (chat != null) {
                val chatUpdatedEvent = SocketMessage.ChatUpdated(chat)

                val participantUserIds = chat.users.map { it.userId }
                RoomManager.broadcastToChatParticipants(
                    chatId,
                    participantUserIds,
                    chatUpdatedEvent
                )
            }
        }

        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    post("/{chatId}/add-user/{targetUserId}") {
        val chatId = call.parameters["chatId"]
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                SimpleResponse(HttpStatusCode.BadRequest.value, "chatId missing")
            )

        val targetUserId = call.parameters["targetUserId"]
            ?: return@post call.respond(
                HttpStatusCode.BadRequest,
                SimpleResponse(HttpStatusCode.BadRequest.value, "targetUserId missing")
            )

        val accessToken = call.attributes.getOrNull(JwtTokenKey)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "No token")
            )

        val requesterUserId = extractRequesterUserId(tokenRepository, accessToken)
            ?: return@post call.respond(
                HttpStatusCode.Unauthorized,
                SimpleResponse(HttpStatusCode.Unauthorized.value, "Invalid token")
            )

        val response = chatRepository.addUserInChat(
            chatId = chatId,
            requesterUserId = requesterUserId,
            targetUserId = targetUserId
        )

        if (response.status == HttpStatusCode.OK.value) {
            val userMetadata = try {
                val metadataJson = response.data
                if (metadataJson != null) {
                    Json.decodeFromJsonElement(com.piedpiper.features.chat.data.models.UserMetadata.serializer(), metadataJson)
                } else null
            } catch (e: Exception) {
                null
            }
            
            if (userMetadata != null) {
                val userAddedEvent = SocketMessage.UserAddedToChat(
                    chatId = chatId,
                    userId = targetUserId,
                    userMetadata = userMetadata
                )

                val getUserChatsResponse = chatRepository.getUserChats(requesterUserId)
                if (getUserChatsResponse.status == HttpStatusCode.OK.value) {
                    val chats: List<Chat>? = try {
                        val chatsJson = getUserChatsResponse.data
                        if (chatsJson != null) {
                            Json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(Chat.serializer()), chatsJson)
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                    
                    val chat = chats?.find { it.id == chatId }
                    if (chat != null) {
                        val participantUserIds = chat.users.map { it.userId }
                        RoomManager.broadcastToChatParticipants(
                            chatId,
                            participantUserIds,
                            userAddedEvent
                        )
                    }
                }
            }
        }

        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

}
