package com.piedpiper.features.friends.data.socket

import com.piedpiper.common.SimpleResponse
import com.piedpiper.features.chat.data.models.UserMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
sealed class FriendSocketMessage {
    // Исходящие события (от сервера к клиенту)
    @Serializable
    @SerialName("friend_request_sent")
    data class FriendRequestSent(val fromUserId: String, val toUserId: String) : FriendSocketMessage()

    @Serializable
    @SerialName("friend_request_accepted")
    data class FriendRequestAccepted(val friendMetadata: UserMetadata) : FriendSocketMessage()

    @Serializable
    @SerialName("friend_removed")
    data class FriendRemoved(val userId: String) : FriendSocketMessage()

    // Входящие команды (от клиента к серверу)
    @Serializable
    @SerialName("send_friend_request")
    data class SendFriendRequest(val targetUserId: String) : FriendSocketMessage()

    @Serializable
    @SerialName("accept_friend_request")
    data class AcceptFriendRequest(val targetUserId: String) : FriendSocketMessage()

    @Serializable
    @SerialName("decline_friend_request")
    data class DeclineFriendRequest(val targetUserId: String) : FriendSocketMessage()

    @Serializable
    @SerialName("remove_friend")
    data class RemoveFriend(val targetUserId: String) : FriendSocketMessage()

    @Serializable
    @SerialName("error_message")
    data class ErrorMessage(val simpleResponse: SimpleResponse) : FriendSocketMessage()
}

