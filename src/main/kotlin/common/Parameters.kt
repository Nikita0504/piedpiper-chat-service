package com.piedpiper.common

data class Parameters(
    val DATABASE: String = System.getenv("DATABASE") ?: "PiedPierBase",
    val POSTGRES_HOST: String = System.getenv("POSTGRES_HOST") ?: "192.168.0.3",
    val POSTGRES_PORT: String = System.getenv("POSTGRES_PORT") ?: "5432",
    val POSTGRES_USER: String = System.getenv("POSTGRES_USER") ?: "postgres",
    val POSTGRES_PASSWORD: String = System.getenv("POSTGRES_PASSWORD") ?: "root",
    val USER_SERVICE: String = System.getenv("USER_SERVICE") ?: "http://192.168.0.3:8081/PiedPiper/api/v1",
    val AUTH_SERVICE: String = System.getenv("AUTH_SERVICE") ?: "http://192.168.0.3:8082/PiedPiper/api/v1/user"
) {
    val POSTGRES_URL: String =
        "jdbc:postgresql://$POSTGRES_HOST:$POSTGRES_PORT/$DATABASE"
}