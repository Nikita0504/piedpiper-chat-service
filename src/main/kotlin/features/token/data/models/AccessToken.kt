package com.piedpiper.features.token.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AccessToken(
    val accessToken: String,
)