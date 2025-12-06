package com.piedpiper.features.friends.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.common.runWithDefaultOnException
import com.piedpiper.features.chat.data.models.UserMetadata
import com.piedpiper.features.friends.data.models.FriendList
import com.piedpiper.features.friends.data.repository.FriendRepository
import com.piedpiper.features.user.data.models.User
import com.piedpiper.features.user.data.repository.UserDataRepository
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.eq

class FriendService(
    dataBase: CoroutineDatabase,
    val userRepository: UserDataRepository
) : FriendRepository {

    private val friendListCollection = dataBase.getCollection<FriendList>()

    private suspend fun getUserMetadata(userId: String): UserMetadata? {
        val userResponse = userRepository.getUserById(userId)
        val userDataJsonEl = userResponse.data

        if (userDataJsonEl != null && userDataJsonEl !is JsonNull) {
            return try {
                val user: User = Json.decodeFromJsonElement(userDataJsonEl)
                UserMetadata(
                    userId = user.id,
                    avatarUrl = user.avatarUrl
                )
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private suspend fun getOrCreateFriendList(userId: String): FriendList {
        val existing = friendListCollection.findOne(FriendList::userId eq userId)
        if (existing != null) {
            return existing
        }
        val newList = FriendList(userId = userId)
        friendListCollection.insertOne(newList)
        return newList
    }

    override suspend fun getFriends(requesterUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while getting friends: ") {
            val friendList = friendListCollection.findOne(FriendList::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "Friends list is empty",
                    data = Json.encodeToJsonElement(emptyList<UserMetadata>())
                )

            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "Friends retrieved successfully",
                data = Json.encodeToJsonElement(friendList.friends)
            )
        }
    }

    override suspend fun getFriendRequests(requesterUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while getting friend requests: ") {
            val friendList = friendListCollection.findOne(FriendList::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "No friend requests",
                    data = Json.encodeToJsonElement(emptyList<UserMetadata>())
                )

            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "Friend requests retrieved successfully",
                data = Json.encodeToJsonElement(friendList.friendRequests)
            )
        }
    }

    override suspend fun sendFriendRequest(requesterUserId: String, targetUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while sending friend request: ") {
            if (requesterUserId == targetUserId) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.BadRequest.value,
                    message = "Cannot send friend request to yourself"
                )
            }

            val targetMetadata = getUserMetadata(targetUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Target user not found"
                )

            val requesterFriendList = getOrCreateFriendList(requesterUserId)
            
            if (requesterFriendList.friends.any { it.userId == targetUserId }) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Conflict.value,
                    message = "User is already a friend"
                )
            }

            if (requesterFriendList.friendRequests.any { it.userId == targetUserId }) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.Conflict.value,
                    message = "Friend request already sent"
                )
            }

            val targetFriendList = getOrCreateFriendList(targetUserId)
            val updatedTargetFriendList = targetFriendList.addFriendRequest(
                UserMetadata(
                    userId = requesterUserId,
                    avatarUrl = getUserMetadata(requesterUserId)?.avatarUrl
                )
            )

            val isTargetUpdateSuccess = if (targetFriendList.userId == targetUserId && targetFriendList.friends.isEmpty() && targetFriendList.friendRequests.isEmpty()) {
                friendListCollection.insertOne(updatedTargetFriendList).wasAcknowledged()
            } else {
                friendListCollection.updateOne(FriendList::userId eq targetUserId, updatedTargetFriendList).wasAcknowledged()
            }

            if (!isTargetUpdateSuccess) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Failed to send friend request"
                )
            }

            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "Friend request sent successfully"
            )
        }
    }

    override suspend fun acceptFriendRequest(requesterUserId: String, targetUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while accepting friend request: ") {
            val requesterFriendList = friendListCollection.findOne(FriendList::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend list not found"
                )

            if (!requesterFriendList.friendRequests.any { it.userId == targetUserId }) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend request not found"
                )
            }

            val targetMetadata = getUserMetadata(targetUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Target user not found"
                )

            val requesterMetadata = getUserMetadata(requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Requester user not found"
                )

            val updatedRequesterFriendList = requesterFriendList.acceptFriendRequest(targetUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Failed to accept friend request"
                )

            val isRequesterUpdateSuccess = friendListCollection.updateOne(
                FriendList::userId eq requesterUserId,
                updatedRequesterFriendList
            ).wasAcknowledged()

            val targetFriendList = getOrCreateFriendList(targetUserId)
            val updatedTargetFriendList = targetFriendList
                .addFriend(requesterMetadata)
                .removeFriendRequest(requesterUserId)

            val isTargetUpdateSuccess = if (targetFriendList.userId == targetUserId && targetFriendList.friends.isEmpty() && targetFriendList.friendRequests.isEmpty()) {
                friendListCollection.insertOne(updatedTargetFriendList).wasAcknowledged()
            } else {
                friendListCollection.updateOne(FriendList::userId eq targetUserId, updatedTargetFriendList).wasAcknowledged()
            }

            if (isRequesterUpdateSuccess && isTargetUpdateSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "Friend request accepted successfully",
                    data = Json.encodeToJsonElement(targetMetadata)
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Failed to accept friend request"
                )
            }
        }
    }

    override suspend fun declineFriendRequest(requesterUserId: String, targetUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while declining friend request: ") {
            val requesterFriendList = friendListCollection.findOne(FriendList::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend list not found"
                )

            if (!requesterFriendList.friendRequests.any { it.userId == targetUserId }) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend request not found"
                )
            }

            val updatedFriendList = requesterFriendList.removeFriendRequest(targetUserId)
            val isSuccess = friendListCollection.updateOne(
                FriendList::userId eq requesterUserId,
                updatedFriendList
            ).wasAcknowledged()

            if (isSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "Friend request declined successfully"
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Failed to decline friend request"
                )
            }
        }
    }

    override suspend fun removeFriend(requesterUserId: String, targetUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while removing friend: ") {
            val requesterFriendList = friendListCollection.findOne(FriendList::userId eq requesterUserId)
                ?: return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend list not found"
                )

            if (!requesterFriendList.friends.any { it.userId == targetUserId }) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend not found"
                )
            }

            val updatedRequesterFriendList = requesterFriendList.removeFriend(targetUserId)
            val isRequesterUpdateSuccess = friendListCollection.updateOne(
                FriendList::userId eq requesterUserId,
                updatedRequesterFriendList
            ).wasAcknowledged()

            val targetFriendList = friendListCollection.findOne(FriendList::userId eq targetUserId)
            val isTargetUpdateSuccess = if (targetFriendList != null) {
                val updatedTargetFriendList = targetFriendList.removeFriend(requesterUserId)
                friendListCollection.updateOne(
                    FriendList::userId eq targetUserId,
                    updatedTargetFriendList
                ).wasAcknowledged()
            } else {
                true
            }

            if (isRequesterUpdateSuccess && isTargetUpdateSuccess) {
                SimpleResponse(
                    status = HttpStatusCode.OK.value,
                    message = "Friend removed successfully"
                )
            } else {
                SimpleResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Failed to remove friend"
                )
            }
        }
    }
}

