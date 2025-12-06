package com.piedpiper.features.chat.data.repository

import com.piedpiper.common.SimpleResponse
import com.piedpiper.features.chat.data.models.Message

interface MessageRepository {
    suspend fun getMessages(chatId: String, afterTimestamp: Long?, limit: Int = 50): SimpleResponse
    suspend fun sendMessage(chatId: String, requesterUserId: String, message: Message): SimpleResponse
    suspend fun updateMessage(chatId: String, message: Message): SimpleResponse
    suspend fun deleteMessage(chatId: String, messageId: String): SimpleResponse
}