# PiedPiper Chat Service

A real-time chat microservice built with Kotlin and Ktor, providing WebSocket-based messaging, chat management, and friend system functionality.

## 🚀 Features

- **Real-time Messaging**: WebSocket-based messaging with support for text and media files
- **Chat Management**: Create private chats and group chats with unlimited participants
- **Friend System**: Send, accept, and manage friend requests with real-time notifications
- **Secure Authentication**: JWT token-based authentication for all endpoints
- **Scalable Architecture**: Clean separation of concerns with repository and service layers
- **MongoDB Integration**: Efficient data storage with separate collections for chats, messages, and friend lists

## 📋 Table of Contents

- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Getting Started](#-getting-started)
- [API Documentation](#-api-documentation)
- [WebSocket Endpoints](#-websocket-endpoints)
- [Project Structure](#-project-structure)
- [Features in Detail](#-features-in-detail)

## 🛠 Tech Stack

- **Language**: Kotlin
- **Framework**: Ktor
- **Database**: MongoDB (via KMongo)
- **Dependency Injection**: Koin
- **Serialization**: Kotlinx Serialization
- **WebSocket**: Ktor WebSocket

## 🏗 Architecture

The service follows a clean architecture pattern with clear separation:

```
features/
├── chat/          # Chat functionality
│   ├── data/
│   │   ├── models/        # Chat, Message, UserMetadata models
│   │   ├── repository/    # ChatRepository interface
│   │   ├── services/      # ChatService, MessageService
│   │   └── socket/        # WebSocket management (RoomManager, ClientSession)
│   └── ChatRoute.kt       # HTTP and WebSocket routes
├── friends/       # Friends functionality
│   ├── data/
│   │   ├── models/        # FriendList model
│   │   ├── repository/   # FriendRepository interface
│   │   ├── services/      # FriendService
│   │   └── socket/        # Friend WebSocket management
│   └── FriendRoute.kt     # HTTP and WebSocket routes
├── user/          # User data integration (external service)
└── token/         # Token validation (external service)
```

## 🚦 Getting Started

### Prerequisites

- JDK 17 or higher
- MongoDB instance
- Access to User Service and Token Service (for user data and token validation)

### Configuration

Configure your application in `src/main/resources/application.yaml`:

```yaml
# Add your configuration here
# Database connection
# External service URLs (user service, token service)
```

### Running the Application

```bash
./gradlew run
```

The service will start on `http://localhost:8080` by default.

## 📚 API Documentation

For complete API documentation, including all endpoints, request/response formats, and WebSocket protocols, see:

**[API_DOCUMENTATION.md](./API_DOCUMENTATION.md)**

## 🔌 WebSocket Endpoints

The service provides three separate WebSocket endpoints for different purposes:

### 1. Chat Metadata (`/chat/ws/chats`)
- **Purpose**: Receive real-time updates about chat metadata
- **Auto-subscription**: Automatically subscribed to all user's chats
- **Events**: `new_chat`, `chat_updated`, `user_added_to_chat`, `user_left_chat`

### 2. Messages (`/chat/ws/messages`)
- **Purpose**: Send and receive messages in real-time
- **Manual subscription**: Subscribe to specific chats to receive messages
- **Commands**: `subscribe_to_messages`, `new_message`, `update_message`, `delete_message`
- **Events**: `new_message`, `update_message`, `delete_message`

### 3. Friends (`/ws/friends`)
- **Purpose**: Manage friends and receive friend-related events
- **Auto-subscription**: Automatically subscribed to friend events
- **Commands**: `send_friend_request`, `accept_friend_request`, `decline_friend_request`, `remove_friend`
- **Events**: `friend_request_sent`, `friend_request_accepted`, `friend_removed`

## 📁 Project Structure

```
src/main/kotlin/
├── Application.kt              # Application entry point
├── common/                     # Common utilities
├── features/
│   ├── chat/                   # Chat feature
│   │   ├── ChatRoute.kt       # Chat HTTP/WebSocket routes
│   │   ├── ChatHelpers.kt     # Helper functions
│   │   ├── data/
│   │   │   ├── models/         # Data models
│   │   │   ├── repository/     # Repository interfaces
│   │   │   ├── services/      # Business logic
│   │   │   └── socket/        # WebSocket management
│   │   └── di/                # Dependency injection
│   ├── friends/               # Friends feature
│   │   ├── FriendRoute.kt     # Friends HTTP/WebSocket routes
│   │   ├── data/
│   │   │   ├── models/
│   │   │   ├── repository/
│   │   │   ├── services/
│   │   │   └── socket/
│   │   └── di/
│   ├── user/                  # User service integration
│   ├── token/                 # Token service integration
│   └── database/              # Database configuration
└── plugins/                   # Ktor plugins configuration
```

## ✨ Features in Detail

### Chat System

- **Unified Chat Creation**: Single endpoint for creating both private chats (2 users) and group chats (unlimited users)
- **Duplicate Prevention**: Automatic check for existing private chats between two users
- **Real-time Updates**: All chat metadata changes are broadcasted to participants in real-time
- **User Management**: Add users to chats with automatic notifications

### Message System

- **Real-time Messaging**: Instant message delivery via WebSocket
- **Message Types**: Support for text messages and WebRTC signals
- **Media Support**: File metadata structure ready for media file handling
- **Message Operations**: Send, update, and delete messages with real-time synchronization

### Friend System

- **Friend Requests**: Send and manage friend requests
- **Real-time Notifications**: Instant notifications for friend-related events
- **Bidirectional Friendship**: When a request is accepted, both users are added to each other's friend lists
- **Privacy**: Declining a request doesn't notify the requester

### Security

- **JWT Authentication**: All endpoints require valid JWT tokens
- **Authorization Checks**: Users can only access chats they're members of
- **WebSocket Security**: Token validation for all WebSocket connections
- **Member Verification**: Subscription to messages requires chat membership verification

## 🔐 Authentication

All requests require a JWT token in the `Authorization` header:

```
Authorization: Bearer <your-jwt-token>
```

The token is validated through an external token service, and the user ID is extracted from the token payload.

## 📊 Data Models

### Chat
- Stores chat metadata (name, description, avatar)
- Contains list of participants with minimal user data (userId, avatarUrl)
- Automatically determines if chat is private (2 users) or group (>2 users)

### Message
- Supports text and WebRTC signal types
- Includes file metadata structure for future media support
- Timestamped for chronological ordering

### FriendList
- Stores user's friends and pending friend requests
- Minimal metadata (userId, avatarUrl) for efficient storage
- Separate collections for friends and requests

## 🧪 Development

### Building

```bash
./gradlew build
```



---

For detailed API documentation, see [API_DOCUMENTATION.md](./API_DOCUMENTATION.md)
