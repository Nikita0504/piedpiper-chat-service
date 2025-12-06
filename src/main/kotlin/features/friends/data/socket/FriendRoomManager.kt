package com.piedpiper.features.friends.data.socket

import com.piedpiper.features.chat.data.socket.ClientSession
import kotlinx.serialization.json.Json

object FriendRoomManager {
    // Подписки пользователей на события друзей (по userId)
    private val userSubscriptions: MutableMap<String, MutableSet<ClientSession>> = mutableMapOf()

    /**
     * Подписать пользователя на события друзей
     */
    fun subscribeUser(userId: String, session: ClientSession) {
        userSubscriptions.getOrPut(userId) { mutableSetOf() }.add(session)
    }

    /**
     * Отписать пользователя от событий друзей
     */
    fun unsubscribeUser(userId: String, session: ClientSession) {
        userSubscriptions[userId]?.remove(session)
        if (userSubscriptions[userId]?.isEmpty() == true) {
            userSubscriptions.remove(userId)
        }
    }

    /**
     * Отправить событие пользователю
     */
    fun sendToUser(userId: String, message: FriendSocketMessage) {
        val json = Json.encodeToString(FriendSocketMessage.serializer(), message)
        userSubscriptions[userId]?.forEach { session ->
            session.trySendRaw(json)
        }
    }

    /**
     * Отправить событие нескольким пользователям
     */
    fun sendToUsers(userIds: List<String>, message: FriendSocketMessage) {
        val json = Json.encodeToString(FriendSocketMessage.serializer(), message)
        userIds.forEach { userId ->
            userSubscriptions[userId]?.forEach { session ->
                session.trySendRaw(json)
            }
        }
    }
}

