package com.piedpiper.features.chat.data.repository

import com.piedpiper.common.SimpleResponse
import com.piedpiper.features.chat.data.models.Chat

interface ChatRepository {
    suspend fun getUserChats(requesterUserId: String): SimpleResponse
    suspend fun createChat(
        participantUserIds: List<String>,
        requesterUserId: String,
        chatName: String? = null,
        description: String? = null,
        avatarUrl: String? = null
    ): SimpleResponse
    suspend fun updateChat(
        chatId: String,
        requesterUserId: String,
        chatName: String? = null,
        description: String? = null,
        avatarUrl: String? = null
    ): SimpleResponse
    suspend fun addUserInChat(
        chatId: String,
        requesterUserId: String,
        targetUserId: String
    ): SimpleResponse
    suspend fun leaveChat(
        chatId: String,
        requesterUserId: String
    ): SimpleResponse
}
