package com.piedpiper.common

data class Parameters(
    val DATABASE: String = System.getenv("DATABASE") ?: "PiedPiperBase",
    val MONGODB : String = System.getenv("MONGODB") ?: "mongodb://localhost:27017",
    val USER_SERVICE: String = System.getenv("USER_SERVICE") ?: "http://0.0.0.0:8081/PiedPiper/api/v1",
    val AUTH_SERVICE: String = System.getenv("AUTH_SERVICE") ?: "http://0.0.0.0:8080/PiedPiper/api/v1/user"
)