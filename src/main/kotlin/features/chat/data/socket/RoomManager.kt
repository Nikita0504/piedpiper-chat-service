package com.piedpiper.features.chat.data.socket

import features.chat.data.socket.SocketMessage
import kotlinx.serialization.json.Json

object RoomManager {
    private val messageSubscriptions: MutableMap<String, MutableSet<ClientSession>> = mutableMapOf()
    
    private val userSubscriptions: MutableMap<String, MutableSet<ClientSession>> = mutableMapOf()


    fun subscribeUserToAllChats(userId: String, session: ClientSession) {
        userSubscriptions.getOrPut(userId) { mutableSetOf() }.add(session)
    }

    fun unsubscribeUserFromAllChats(userId: String, session: ClientSession) {
        userSubscriptions[userId]?.remove(session)
        if (userSubscriptions[userId]?.isEmpty() == true) {
            userSubscriptions.remove(userId)
        }
    }


    fun subscribeToMessages(chatId: String, session: ClientSession) {
        messageSubscriptions.getOrPut(chatId) { mutableSetOf() }.add(session)
        session.subscribeToMessages(chatId)
    }


    fun unsubscribeFromMessages(chatId: String, session: ClientSession) {
        messageSubscriptions[chatId]?.remove(session)
        session.unsubscribeFromMessages(chatId)
        if (messageSubscriptions[chatId]?.isEmpty() == true) {
            messageSubscriptions.remove(chatId)
        }
    }


    fun broadcastToChatParticipants(chatId: String, participantUserIds: List<String>, message: SocketMessage) {
        val json = Json.encodeToString(SocketMessage.serializer(), message)
        participantUserIds.forEach { userId ->
            userSubscriptions[userId]?.forEach { session ->
                session.trySendRaw(json)
            }
        }
    }


    fun broadcastToSubscribed(chatId: String, message: SocketMessage) {
        val json = Json.encodeToString(SocketMessage.serializer(), message)
        messageSubscriptions[chatId]?.forEach { session ->
            session.trySendRaw(json)
        }
    }
}
