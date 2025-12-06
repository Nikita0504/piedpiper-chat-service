package com.piedpiper.features.chat.data.models

import kotlinx.serialization.Serializable
import org.bson.codecs.pojo.annotations.BsonId
import org.bson.types.ObjectId

@Serializable
data class UserMetadata(
    val userId: String,
    val avatarUrl: String?
)

@Serializable
data class Chat(
    @BsonId
    var id: String = ObjectId().toString(),
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