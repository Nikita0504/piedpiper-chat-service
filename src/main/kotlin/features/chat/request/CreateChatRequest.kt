package features.chat.request

import kotlinx.serialization.Serializable

@Serializable
data class CreateChatRequest(
    val participantUserIds: List<String>,
    val chatName: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null
)