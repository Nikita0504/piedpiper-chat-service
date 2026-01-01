package com.piedpiper.features.database

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table


object Chats : UUIDTable("chats") {
    val chatName = varchar("chat_name", 255).nullable()
    val description = text("description").nullable()
    val avatarUrl = text("avatar_url").nullable()
}

object ChatUsers : Table("chat_users") {
    val chatId = uuid("chat_id").references(Chats.id)
    val userId = varchar("user_id", 255)
    val avatarUrl = text("avatar_url").nullable()

    override val primaryKey = PrimaryKey(chatId, userId, name = "PK_CHAT_USERS")
}

object UserInChats : Table("user_in_chats") {
    val userId = varchar("user_id", 255)
    val chatId = uuid("chat_id").references(Chats.id)
    val joinedAt = long("joined_at")

    override val primaryKey = PrimaryKey(userId, chatId, name = "PK_USER_IN_CHATS")
}

object Messages : UUIDTable("messages") {
    val chatId = uuid("chat_id").references(Chats.id)
    val sender = varchar("sender", 255)
    val payload = text("payload")
    val timestamp = long("timestamp")
    val type = varchar("type", 50).default("TEXT")
    val replyText = text("reply_text").nullable()
    val fileName = varchar("file_name", 255).nullable()
    val fileExtension = varchar("file_extension", 50).nullable()
    val fileSize = long("file_size").nullable()
    val extraInformation = text("extra_information").nullable()
    val isUpdateMessage = bool("is_update_message").default(false)
}

object FriendLists : Table("friend_lists") {
    val userId = varchar("user_id", 255)
    val friendUserId = varchar("friend_user_id", 255)
    val friendAvatarUrl = text("friend_avatar_url").nullable()
    val isRequest = bool("is_request").default(false)
    
    override val primaryKey = PrimaryKey(userId, friendUserId, name = "PK_FRIEND_LISTS")
}

