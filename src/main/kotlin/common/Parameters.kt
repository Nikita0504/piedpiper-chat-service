package com.piedpiper.common

data class Parameters(
    val DATABASE: String = System.getenv("DATABASE") ?: "PiedPiperBase",
)