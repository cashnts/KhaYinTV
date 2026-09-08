package dev.khayin.app.data.remote.api

import dev.khayin.app.data.remote.dto.SubtitleHeartbeatRequestDto
import dev.khayin.app.data.remote.dto.SubtitleHeartbeatResponseDto
import dev.khayin.app.data.remote.dto.SubtitleTranslationStatusDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface SubtitleJitApi {
    @Headers("Content-Type: application/json")
    @POST("api/playback/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: SubtitleHeartbeatRequestDto,
        @Header("User-Agent") userAgent: String = "KhaYin/TV"
    ): Response<SubtitleHeartbeatResponseDto>

    @GET("api/translation/status/{id}")
    suspend fun getTranslationStatus(
        @Path("id") id: String,
        @Header("User-Agent") userAgent: String = "KhaYin/TV"
    ): Response<SubtitleTranslationStatusDto>
}
