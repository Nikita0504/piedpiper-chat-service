package com.piedpiper.features.user.data.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class User(
    val id: String,
    val username: String,
    val email: String,
    val fullName: String,
    val role: UserRole,
    val avatarUrl: String? = null,
    val description: String? = null,
)

@Serializable
enum class UserRole {
    USER,
    ADMIN
}
