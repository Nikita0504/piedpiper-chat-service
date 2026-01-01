package com.piedpiper.features.chat.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserMetadata(
    val userId: String,
    val avatarUrl: String?
)

@Serializable
data class Chat(
    var id: String = UUID.randomUUID().toString(),
    val users: List<UserMetadata>,
    val chatName: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
){
    fun addUserMetadata(userMetadata: UserMetadata): Chat {
        return copy(users = users + userMetadata)
    }
    
    fun isPrivateChat(): Boolean {
        return users.size == 2
    }
}