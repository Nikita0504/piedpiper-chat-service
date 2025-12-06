package com.piedpiper.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SimpleResponse(
    val status: Int,
    val message: String,
    val data: JsonElement? = null
)