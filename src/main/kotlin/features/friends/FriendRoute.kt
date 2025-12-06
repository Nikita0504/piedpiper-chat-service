package com.piedpiper.features.friends

import com.piedpiper.common.SimpleResponse
import com.piedpiper.features.chat.data.socket.ClientSession
import com.piedpiper.features.chat.extractRequesterUserId
import com.piedpiper.features.friends.data.repository.FriendRepository
import com.piedpiper.features.friends.data.socket.FriendRoomManager
import com.piedpiper.features.friends.data.socket.FriendSocketMessage
import com.piedpiper.features.token.data.repository.TokenRepository
import com.piedpiper.plugins.JwtTokenKey
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
import kotlinx.serialization.json.decodeFromJsonElement
import org.koin.ktor.ext.get

fun Route.friendRoute() {
    val friendRepository: FriendRepository = get<FriendRepository>()
    val tokenRepository: TokenRepository = get<TokenRepository>()
    val userRepository = get<com.piedpiper.features.user.data.repository.UserDataRepository>()

    webSocket("/ws/friends") {
        val accessToken = call.attributes.getOrNull(JwtTokenKey) ?: return@webSocket close(
            CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No token")
        )

        val requesterUserId = extractRequesterUserId(tokenRepository, accessToken)
            ?: return@webSocket close(
                CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token")
            )

        val clientSession = ClientSession(this, requesterUserId)

        FriendRoomManager.subscribeUser(requesterUserId, clientSession)

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    try {
                        val socketMessage = Json.decodeFromString<FriendSocketMessage>(text)
                        val response = when (socketMessage) {
                            is FriendSocketMessage.SendFriendRequest -> {
                                friendRepository.sendFriendRequest(requesterUserId, socketMessage.targetUserId)
                            }
                            is FriendSocketMessage.AcceptFriendRequest -> {
                                friendRepository.acceptFriendRequest(requesterUserId, socketMessage.targetUserId)
                            }
                            is FriendSocketMessage.DeclineFriendRequest -> {
                                friendRepository.declineFriendRequest(requesterUserId, socketMessage.targetUserId)
                            }
                            is FriendSocketMessage.RemoveFriend -> {
                                friendRepository.removeFriend(requesterUserId, socketMessage.targetUserId)
                            }
                            else -> null
                        }

                        if (response == null) {
                            val errorMsg = FriendSocketMessage.ErrorMessage(
                                simpleResponse = SimpleResponse(
                                    HttpStatusCode.BadRequest.value,
                                    "Unknown message type"
                                )
                            )
                            val errorJson = Json.encodeToString(FriendSocketMessage.serializer(), errorMsg)
                            clientSession.trySendRaw(errorJson)
                        } else if (response.status == HttpStatusCode.OK.value) {
                            when (socketMessage) {
                                is FriendSocketMessage.SendFriendRequest -> {
                                    val friendRequestSentEvent = FriendSocketMessage.FriendRequestSent(
                                        fromUserId = requesterUserId,
                                        toUserId = socketMessage.targetUserId
                                    )
                                    FriendRoomManager.sendToUser(socketMessage.targetUserId, friendRequestSentEvent)
                                }
                                is FriendSocketMessage.AcceptFriendRequest -> {
                                    val friendMetadata = try {
                                        val metadataJson = response.data
                                        if (metadataJson != null) {
                                            Json.decodeFromJsonElement<com.piedpiper.features.chat.data.models.UserMetadata>(metadataJson)
                                        } else null
                                    } catch (e: Exception) {
                                        null
                                    }

                                    if (friendMetadata != null) {
                                        val requesterEvent = FriendSocketMessage.FriendRequestAccepted(friendMetadata)
                                        FriendRoomManager.sendToUser(requesterUserId, requesterEvent)

                                        val requesterUserResponse = userRepository.getUserById(requesterUserId)
                                        val requesterUserJson = requesterUserResponse.data
                                        val requesterUserMetadata = if (requesterUserJson != null) {
                                            try {
                                                val requesterUser = Json.decodeFromJsonElement<com.piedpiper.features.user.data.models.User>(requesterUserJson)
                                                com.piedpiper.features.chat.data.models.UserMetadata(
                                                    userId = requesterUser.id,
                                                    avatarUrl = requesterUser.avatarUrl
                                                )
                                            } catch (e: Exception) {
                                                null
                                            }
                                        } else null

                                        if (requesterUserMetadata != null) {
                                            val targetEvent = FriendSocketMessage.FriendRequestAccepted(requesterUserMetadata)
                                            FriendRoomManager.sendToUser(socketMessage.targetUserId, targetEvent)
                                        }
                                    }
                                }
                                is FriendSocketMessage.RemoveFriend -> {
                                    val requesterEvent = FriendSocketMessage.FriendRemoved(socketMessage.targetUserId)
                                    FriendRoomManager.sendToUser(requesterUserId, requesterEvent)

                                    val targetEvent = FriendSocketMessage.FriendRemoved(requesterUserId)
                                    FriendRoomManager.sendToUser(socketMessage.targetUserId, targetEvent)
                                }
                                else -> {

                                }
                            }
                        } else {
                            val errorMsg = FriendSocketMessage.ErrorMessage(
                                simpleResponse = SimpleResponse(response.status, response.message)
                            )
                            val errorJson = Json.encodeToString(FriendSocketMessage.serializer(), errorMsg)
                            clientSession.trySendRaw(errorJson)
                        }
                    } catch (e: Exception) {
                        val errorMsg = FriendSocketMessage.ErrorMessage(
                            simpleResponse = SimpleResponse(
                                HttpStatusCode.BadRequest.value,
                                "Invalid message format: ${e.message}"
                            )
                        )
                        val errorJson = Json.encodeToString(FriendSocketMessage.serializer(), errorMsg)
                        clientSession.trySendRaw(errorJson)
                    }
                }
            }
        } finally {
            FriendRoomManager.unsubscribeUser(requesterUserId, clientSession)
        }
    }

    get("/friends") {
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

        val response = friendRepository.getFriends(requesterUserId)
        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    get("/friends/requests") {
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

        val response = friendRepository.getFriendRequests(requesterUserId)
        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    post("/friends/request/{targetUserId}") {
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

        val response = friendRepository.sendFriendRequest(requesterUserId, targetUserId)

        if (response.status == HttpStatusCode.OK.value) {
            val friendRequestSentEvent = FriendSocketMessage.FriendRequestSent(
                fromUserId = requesterUserId,
                toUserId = targetUserId
            )
            FriendRoomManager.sendToUser(targetUserId, friendRequestSentEvent)
        }

        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    post("/friends/accept/{targetUserId}") {
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

        val response = friendRepository.acceptFriendRequest(requesterUserId, targetUserId)

        if (response.status == HttpStatusCode.OK.value) {
            val friendMetadata = try {
                val metadataJson = response.data
                if (metadataJson != null) {
                    Json.decodeFromJsonElement<com.piedpiper.features.chat.data.models.UserMetadata>(metadataJson)
                } else null
            } catch (e: Exception) {
                null
            }

            if (friendMetadata != null) {
                val requesterEvent = FriendSocketMessage.FriendRequestAccepted(friendMetadata)
                FriendRoomManager.sendToUser(requesterUserId, requesterEvent)

                val requesterUserResponse = userRepository.getUserById(requesterUserId)
                val requesterUserJson = requesterUserResponse.data
                val requesterUserMetadata = if (requesterUserJson != null) {
                    try {
                        val requesterUser = Json.decodeFromJsonElement<com.piedpiper.features.user.data.models.User>(requesterUserJson)
                        com.piedpiper.features.chat.data.models.UserMetadata(
                            userId = requesterUser.id,
                            avatarUrl = requesterUser.avatarUrl
                        )
                    } catch (e: Exception) {
                        null
                    }
                } else null
                
                if (requesterUserMetadata != null) {
                    val targetEvent = FriendSocketMessage.FriendRequestAccepted(requesterUserMetadata)
                    FriendRoomManager.sendToUser(targetUserId, targetEvent)
                }
            }
        }

        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    post("/friends/decline/{targetUserId}") {
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

        val response = friendRepository.declineFriendRequest(requesterUserId, targetUserId)

        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }

    // Удалить из друзей
    post("/friends/remove/{targetUserId}") {
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

        val response = friendRepository.removeFriend(requesterUserId, targetUserId)

        if (response.status == HttpStatusCode.OK.value) {
            val requesterEvent = FriendSocketMessage.FriendRemoved(targetUserId)
            FriendRoomManager.sendToUser(requesterUserId, requesterEvent)

            val targetEvent = FriendSocketMessage.FriendRemoved(requesterUserId)
            FriendRoomManager.sendToUser(targetUserId, targetEvent)
        }

        call.respond(
            if (response.status == HttpStatusCode.OK.value) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            response
        )
    }
}

