package com.piedpiper.features.token.data.repository

import com.piedpiper.common.SimpleResponse

interface TokenRepository {

    suspend fun validateAccessToken(accessToken: String): SimpleResponse

}