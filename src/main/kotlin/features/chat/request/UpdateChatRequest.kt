package features.chat.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateChatRequest(
    val chatName: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null
)