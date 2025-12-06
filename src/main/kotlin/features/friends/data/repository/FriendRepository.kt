package com.piedpiper.features.friends.data.repository

import com.piedpiper.common.SimpleResponse

interface FriendRepository {
    suspend fun getFriends(requesterUserId: String): SimpleResponse
    suspend fun getFriendRequests(requesterUserId: String): SimpleResponse
    suspend fun sendFriendRequest(requesterUserId: String, targetUserId: String): SimpleResponse
    suspend fun acceptFriendRequest(requesterUserId: String, targetUserId: String): SimpleResponse
    suspend fun declineFriendRequest(requesterUserId: String, targetUserId: String): SimpleResponse
    suspend fun removeFriend(requesterUserId: String, targetUserId: String): SimpleResponse
}

