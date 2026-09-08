package dev.khayin.app.data.remote.api

import dev.khayin.app.data.remote.dto.AdsNextResponseDto
import retrofit2.Response
import retrofit2.http.GET

interface AdsApi {

    @GET("api/ads/next")
    suspend fun getNextAd(): Response<AdsNextResponseDto>
}
