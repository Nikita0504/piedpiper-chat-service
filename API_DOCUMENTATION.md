# API Documentation - PiedPiper Chat Service

## Base URL
```
http://localhost:8080/PiedPiper/api/v1
```

## Authentication

All requests (except WebSocket) require a JWT token in the header:
```
Authorization: Bearer <token>
```

WebSocket also requires a token, passed via query parameter or header (depends on client implementation).

---

## Chat API

### HTTP Endpoints

#### 1. Get User's Chats
```http
GET /chat/
```

**Response:**
```json
{
  "status": 200,
  "message": "The user's chats were successfully received",
  "data": [
    {
      "id": "chat123",
      "users": [
        {
          "userId": "user1",
          "avatarUrl": "https://example.com/avatar1.jpg"
        },
        {
          "userId": "user2",
          "avatarUrl": "https://example.com/avatar2.jpg"
        }
      ],
      "chatName": "My Chat",
      "description": "Chat description",
      "avatarUrl": "https://example.com/chat-avatar.jpg"
    }
  ]
}
```

#### 2. Get Chat Messages
```http
GET /chat/messages/{chatId}?afterTimestamp=1234567890&limit=50
```

**Parameters:**
- `chatId` (path) - Chat ID
- `afterTimestamp` (query, optional) - Get messages after this timestamp
- `limit` (query, optional, default: 50) - Maximum number of messages

**Response:**
```json
{
  "status": 200,
  "message": "Messages received successfully",
  "data": {
    "messages": [
      {
        "id": "msg123",
        "sender": "user1",
        "payload": "Hello!",
        "timestamp": 1234567890,
        "type": "TEXT",
        "replyText": null,
        "fileMetadata": null,
        "isUpdateMessage": false
      }
    ],
    "hasMore": true
  }
}
```

#### 3. Create Chat
```http
POST /chat/create
Content-Type: application/json
```

**Request Body:**
```json
{
  "participantUserIds": ["user1", "user2", "user3"],
  "chatName": "Group Chat",
  "description": "Group description",
  "avatarUrl": "https://example.com/group-avatar.jpg"
}
```

**Features:**
- `participantUserIds` must include `requesterUserId` (the user creating the chat)
- If 2 participants - checks for duplicate private chats
- If >2 participants - creates a group without duplicate check

**Response:**
```json
{
  "status": 200,
  "message": "The chat was created successfully",
  "data": {
    "id": "chat123",
    "users": [...],
    "chatName": "Group Chat",
    ...
  }
}
```

#### 4. Update Chat Metadata
```http
POST /chat/{chatId}/update
Content-Type: application/json
```

**Request Body:**
```json
{
  "chatName": "New Name",
  "description": "New description",
  "avatarUrl": "https://example.com/new-avatar.jpg"
}
```

**Response:**
```json
{
  "status": 200,
  "message": "The chat has been updated",
  "data": {
    "id": "chat123",
    ...
  }
}
```

#### 5. Add User to Chat
```http
POST /chat/{chatId}/add-user/{targetUserId}
```

**Parameters:**
- `chatId` (path) - Chat ID
- `targetUserId` (path) - User ID to add

**Response:**
```json
{
  "status": 200,
  "message": "The user has been successfully added to the chat",
  "data": {
    "userId": "user3",
    "avatarUrl": "https://example.com/avatar3.jpg"
  }
}
```

---

## WebSocket for Chats

### 1. WebSocket for Chat Metadata
```
WS /PiedPiper/api/v1/chat/ws/chats
```

**Automatic Subscription:** On connection, user is automatically subscribed to all their chats.

**Outgoing Events (from server):**
- `new_chat` - New chat created
- `chat_updated` - Chat metadata updated
- `user_added_to_chat` - User added to chat
- `user_left_chat` - User left chat

**Incoming Commands (from client):**
- `user_left_chat` - Leave chat

**Example Incoming Message (leave chat):**
```json
{
  "type": "user_left_chat",
  "chatId": "chat123",
  "userId": "user1",
  "isPublic": true
}
```

**Example Outgoing Event:**
```json
{
  "type": "new_chat",
  "chat": {
    "id": "chat123",
    "users": [...],
    "chatName": "New Chat",
    ...
  }
}
```

### 2. WebSocket for Messages
```
WS /PiedPiper/api/v1/chat/ws/messages
```

**Manual Subscription:** Need to subscribe to specific chats to receive messages.

**Incoming Commands (from client):**

1. **Subscribe to chat messages:**
```json
{
  "type": "subscribe_to_messages",
  "chatId": "chat123"
}
```

2. **Unsubscribe from messages:**
```json
{
  "type": "unsubscribe_from_messages",
  "chatId": "chat123"
}
```

3. **Send message:**
```json
{
  "type": "new_message",
  "chatId": "chat123",
  "message": {
    "id": "msg123",
    "sender": "user1",
    "payload": "Hello!",
    "timestamp": 1234567890,
    "type": "TEXT",
    "replyText": null,
    "fileMetadata": null,
    "isUpdateMessage": false
  }
}
```

4. **Update message:**
```json
{
  "type": "update_message",
  "chatId": "chat123",
  "message": {
    "id": "msg123",
    "sender": "user1",
    "payload": "Updated text",
    "timestamp": 1234567890,
    "type": "TEXT",
    "isUpdateMessage": true
  }
}
```

5. **Delete message:**
```json
{
  "type": "delete_message",
  "chatId": "chat123",
  "messageId": "msg123"
}
```

**Outgoing Events (from server):**
- `new_message` - New message (only to subscribed users)
- `update_message` - Message updated
- `delete_message` - Message deleted
- `error_message` - Error occurred

**Example Outgoing Event:**
```json
{
  "type": "new_message",
  "chatId": "chat123",
  "message": {
    "id": "msg123",
    "sender": "user1",
    "payload": "Hello!",
    "timestamp": 1234567890,
    "type": "TEXT"
  }
}
```

---

## Friends API

### HTTP Endpoints

#### 1. Get Friends List
```http
GET /friends
```

**Response:**
```json
{
  "status": 200,
  "message": "Friends retrieved successfully",
  "data": [
    {
      "userId": "user2",
      "avatarUrl": "https://example.com/avatar2.jpg"
    },
    {
      "userId": "user3",
      "avatarUrl": "https://example.com/avatar3.jpg"
    }
  ]
}
```

#### 2. Get Friend Requests
```http
GET /friends/requests
```

**Response:**
```json
{
  "status": 200,
  "message": "Friend requests retrieved successfully",
  "data": [
    {
      "userId": "user4",
      "avatarUrl": "https://example.com/avatar4.jpg"
    }
  ]
}
```

#### 3. Send Friend Request
```http
POST /friends/request/{targetUserId}
```

**Parameters:**
- `targetUserId` (path) - User ID to send friend request to

**Response:**
```json
{
  "status": 200,
  "message": "Friend request sent successfully"
}
```

#### 4. Accept Friend Request
```http
POST /friends/accept/{targetUserId}
```

**Parameters:**
- `targetUserId` (path) - User ID whose request to accept

**Response:**
```json
{
  "status": 200,
  "message": "Friend request accepted successfully",
  "data": {
    "userId": "user2",
    "avatarUrl": "https://example.com/avatar2.jpg"
  }
}
```

#### 5. Decline Friend Request
```http
POST /friends/decline/{targetUserId}
```

**Parameters:**
- `targetUserId` (path) - User ID whose request to decline

**Response:**
```json
{
  "status": 200,
  "message": "Friend request declined successfully"
}
```

**Note:** When declining a request, no WebSocket events are sent.

#### 6. Remove Friend
```http
POST /friends/remove/{targetUserId}
```

**Parameters:**
- `targetUserId` (path) - User ID to remove from friends

**Response:**
```json
{
  "status": 200,
  "message": "Friend removed successfully"
}
```

---

## WebSocket for Friends

```
WS /PiedPiper/api/v1/ws/friends
```

**Automatic Subscription:** On connection, user is automatically subscribed to friend events.

### Incoming Commands (from client)

#### 1. Send Friend Request
```json
{
  "type": "send_friend_request",
  "targetUserId": "user2"
}
```

#### 2. Accept Friend Request
```json
{
  "type": "accept_friend_request",
  "targetUserId": "user2"
}
```

#### 3. Decline Friend Request
```json
{
  "type": "decline_friend_request",
  "targetUserId": "user2"
}
```

#### 4. Remove Friend
```json
{
  "type": "remove_friend",
  "targetUserId": "user2"
}
```

### Outgoing Events (from server)

#### 1. Friend Request Sent
```json
{
  "type": "friend_request_sent",
  "fromUserId": "user1",
  "toUserId": "user2"
}
```
**Recipient:** User who received the request (`toUserId`)

#### 2. Friend Request Accepted
```json
{
  "type": "friend_request_accepted",
  "friendMetadata": {
    "userId": "user2",
    "avatarUrl": "https://example.com/avatar2.jpg"
  }
}
```
**Recipients:** Both users (each receives the other's metadata)

#### 3. Friend Removed
```json
{
  "type": "friend_removed",
  "userId": "user2"
}
```
**Recipients:** Both users

#### 4. Error
```json
{
  "type": "error_message",
  "simpleResponse": {
    "status": 400,
    "message": "Error message"
  }
}
```

---

## Usage Examples

### Scenario 1: Create Private Chat and Send Message

1. **Create chat:**
```http
POST /chat/create
{
  "participantUserIds": ["user1", "user2"]
}
```

2. **Connect to WebSocket for metadata:**
```
WS /PiedPiper/api/v1/chat/ws/chats
```
Receive `new_chat` event with created chat data.

3. **Connect to WebSocket for messages:**
```
WS /PiedPiper/api/v1/chat/ws/messages
```

4. **Subscribe to messages:**
```json
{
  "type": "subscribe_to_messages",
  "chatId": "chat123"
}
```

5. **Send message:**
```json
{
  "type": "new_message",
  "chatId": "chat123",
  "message": {
    "sender": "user1",
    "payload": "Hello!",
    "timestamp": 1234567890,
    "type": "TEXT"
  }
}
```

6. **Receive message:**
Both users receive `new_message` event via WebSocket.

### Scenario 2: Working with Friends

1. **Connect to WebSocket for friends:**
```
WS /PiedPiper/api/v1/ws/friends
```

2. **Send friend request:**
```json
{
  "type": "send_friend_request",
  "targetUserId": "user2"
}
```

3. **Receive request notification:**
User `user2` receives event:
```json
{
  "type": "friend_request_sent",
  "fromUserId": "user1",
  "toUserId": "user2"
}
```

4. **Accept request:**
User `user2` sends:
```json
{
  "type": "accept_friend_request",
  "targetUserId": "user1"
}
```

5. **Receive notifications:**
Both users receive `friend_request_accepted` event with each other's metadata.

---

## Error Codes

- `200` - Success
- `400` - Bad Request (invalid data format, missing required parameters)
- `401` - Unauthorized (missing or invalid token)
- `403` - Forbidden (user is not a member of the chat)
- `404` - Not Found (chat, user, or message not found)
- `409` - Conflict (chat already exists, request already sent, user already a friend)

---

## Data Models

### UserMetadata
```json
{
  "userId": "string",
  "avatarUrl": "string | null"
}
```

### Chat
```json
{
  "id": "string",
  "users": [UserMetadata],
  "chatName": "string | null",
  "description": "string | null",
  "avatarUrl": "string | null"
}
```

### Message
```json
{
  "id": "string",
  "sender": "string",
  "payload": "string",
  "timestamp": "number",
  "type": "TEXT | WEBRTC_SIGNAL",
  "replyText": "string | null",
  "fileMetadata": {
    "fileName": "string",
    "fileExtension": "string",
    "fileSize": "number",
    "extraInformation": "string"
  } | null,
  "isUpdateMessage": "boolean"
}
```

---

## Notes

1. **Security:**
   - All requests require a valid JWT token
   - When subscribing to messages, user membership in chat is verified
   - Users can only perform actions on their own behalf

2. **WebSocket:**
   - Each event type has its own WebSocket endpoint
   - Subscriptions are managed automatically or manually depending on type
   - On disconnect, all subscriptions are automatically removed

3. **Friends:**
   - When accepting a request, both users are automatically added to each other's friend lists
   - When declining a request, no events are sent
   - When removing a friend, both users are removed from each other's lists
