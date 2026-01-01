package com.piedpiper.features.friends.data.models

import com.piedpiper.features.chat.data.models.UserMetadata
import kotlinx.serialization.Serializable

@Serializable
data class FriendList(
    val userId: String,
    val friends: List<UserMetadata> = listOf(),
    val friendRequests: List<UserMetadata> = listOf()
) {
    fun addFriend(userMetadata: UserMetadata): FriendList {
        return copy(friends = friends + userMetadata)
    }
    
    fun removeFriend(userId: String): FriendList {
        return copy(friends = friends.filter { it.userId != userId })
    }
    
    fun addFriendRequest(userMetadata: UserMetadata): FriendList {
        return copy(friendRequests = friendRequests + userMetadata)
    }
    
    fun removeFriendRequest(userId: String): FriendList {
        return copy(friendRequests = friendRequests.filter { it.userId != userId })
    }
    
    fun acceptFriendRequest(userId: String): FriendList? {
        val request = friendRequests.find { it.userId == userId } ?: return null
        return copy(
            friends = friends + request,
            friendRequests = friendRequests.filter { it.userId != userId }
        )
    }
}

