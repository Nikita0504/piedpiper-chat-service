package com.piedpiper.features.user.data.repository

import com.piedpiper.common.SimpleResponse

interface UserDataRepository {
    suspend fun getUserByUsername(username: String): SimpleResponse

    suspend fun getAllUsers(): SimpleResponse

    suspend fun getUserById(id: String): SimpleResponse

    suspend fun updateUser(user: com.piedpiper.features.user.data.models.User): SimpleResponse

    suspend fun deleteUserById(id: String): SimpleResponse
}