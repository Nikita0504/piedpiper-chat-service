package com.piedpiper.features.friends.data.services

import com.piedpiper.common.SimpleResponse
import com.piedpiper.common.runWithDefaultOnException
import com.piedpiper.features.chat.data.models.UserMetadata
import com.piedpiper.features.friends.data.models.FriendList
import com.piedpiper.features.friends.data.repository.FriendRepository
import com.piedpiper.features.user.data.models.User
import com.piedpiper.features.user.data.repository.UserDataRepository
import com.piedpiper.features.database.FriendLists
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class FriendService(
    private val database: Database,
    val userRepository: UserDataRepository
) : FriendRepository {

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

    private suspend fun getFriendList(userId: String): FriendList {
        return newSuspendedTransaction(db = database) {
            val friends = FriendLists.select {
                FriendLists.userId eq userId and (FriendLists.isRequest eq false)
            }.map { row ->
                UserMetadata(
                    userId = row[FriendLists.friendUserId],
                    avatarUrl = row[FriendLists.friendAvatarUrl]
                )
            }
            
            val friendRequests = FriendLists.select {
                FriendLists.userId eq userId and (FriendLists.isRequest eq true)
            }.map { row ->
                UserMetadata(
                    userId = row[FriendLists.friendUserId],
                    avatarUrl = row[FriendLists.friendAvatarUrl]
                )
            }
            
            FriendList(
                userId = userId,
                friends = friends,
                friendRequests = friendRequests
            )
        }
    }

    override suspend fun getFriends(requesterUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while getting friends: ") {
            val friendList = getFriendList(requesterUserId)

            SimpleResponse(
                status = HttpStatusCode.OK.value,
                message = "Friends retrieved successfully",
                data = Json.encodeToJsonElement(friendList.friends)
            )
        }
    }

    override suspend fun getFriendRequests(requesterUserId: String): SimpleResponse {
        return runWithDefaultOnException(errorMessage = "An error occurred while getting friend requests: ") {
            val friendList = getFriendList(requesterUserId)

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

            val requesterFriendList = getFriendList(requesterUserId)
            
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

            val requesterMetadata = getUserMetadata(requesterUserId)
            val success = newSuspendedTransaction(db = database) {
                try {
                    FriendLists.insert {
                        it[FriendLists.userId] = targetUserId
                        it[FriendLists.friendUserId] = requesterUserId
                        it[FriendLists.friendAvatarUrl] = requesterMetadata?.avatarUrl
                        it[FriendLists.isRequest] = true
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (!success) {
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
            val requesterFriendList = getFriendList(requesterUserId)
            
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

            val success = newSuspendedTransaction(db = database) {
                try {
                    FriendLists.deleteWhere {
                        FriendLists.userId eq requesterUserId and (FriendLists.friendUserId eq targetUserId) and (FriendLists.isRequest eq true)
                    }
                    
                    FriendLists.insert {
                        it[FriendLists.userId] = requesterUserId
                        it[FriendLists.friendUserId] = targetUserId
                        it[FriendLists.friendAvatarUrl] = targetMetadata.avatarUrl
                        it[FriendLists.isRequest] = false
                    }
                    
                    FriendLists.insert {
                        it[FriendLists.userId] = targetUserId
                        it[FriendLists.friendUserId] = requesterUserId
                        it[FriendLists.friendAvatarUrl] = requesterMetadata.avatarUrl
                        it[FriendLists.isRequest] = false
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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
            val requesterFriendList = getFriendList(requesterUserId)
            
            if (!requesterFriendList.friendRequests.any { it.userId == targetUserId }) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend request not found"
                )
            }

            val success = newSuspendedTransaction(db = database) {
                try {
                    FriendLists.deleteWhere {
                        FriendLists.userId eq requesterUserId and (FriendLists.friendUserId eq targetUserId) and (FriendLists.isRequest eq true)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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
            val requesterFriendList = getFriendList(requesterUserId)
            
            if (!requesterFriendList.friends.any { it.userId == targetUserId }) {
                return@runWithDefaultOnException SimpleResponse(
                    status = HttpStatusCode.NotFound.value,
                    message = "Friend not found"
                )
            }

            val success = newSuspendedTransaction(db = database) {
                try {
                    FriendLists.deleteWhere {
                        FriendLists.userId eq requesterUserId and (FriendLists.friendUserId eq targetUserId) and (FriendLists.isRequest eq false)
                    }
                    
                    FriendLists.deleteWhere {
                        FriendLists.userId eq targetUserId and (FriendLists.friendUserId eq requesterUserId) and (FriendLists.isRequest eq false)
                    }
                    
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
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
