package com.piedpiper.features.chat.data.socket

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class ClientSession(
    val session: DefaultWebSocketServerSession,
    val userId: String
) {
    val outgoing: Channel<String> = Channel(capacity = 16)
    val subscribedChatIds: MutableSet<String> = mutableSetOf()

    init {
        GlobalScope.launch {
            for (msg in outgoing) {
                session.send(msg)
            }
        }
    }

    fun trySendRaw(json: String) {
        outgoing.trySend(json)
    }
    
    fun subscribeToMessages(chatId: String) {
        subscribedChatIds.add(chatId)
    }
    
    fun unsubscribeFromMessages(chatId: String) {
        subscribedChatIds.remove(chatId)
    }
}

