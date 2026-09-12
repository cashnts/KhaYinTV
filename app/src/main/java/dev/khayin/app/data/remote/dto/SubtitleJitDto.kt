package dev.khayin.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubtitleHeartbeatRequestDto(
    @Json(name = "slug") val slug: String = "anonymous",
    @Json(name = "type") val type: String,
    @Json(name = "id") val id: String,
    @Json(name = "currentTime") val currentTime: Double,
    @Json(name = "isPlaying") val isPlaying: Boolean,
    @Json(name = "duration") val duration: Double? = null
)

@JsonClass(generateAdapter = true)
data class SubtitleHeartbeatResponseDto(
    @Json(name = "status") val status: String? = null,
    @Json(name = "activeUsers") val activeUsers: Int? = null,
    @Json(name = "isActivelyWatched") val isActivelyWatched: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class SubtitleTranslationStatusDto(
    @Json(name = "mediaId") val mediaId: String? = null,
    @Json(name = "inFlight") val inFlight: Boolean? = null,
    @Json(name = "isPaused") val isPaused: Boolean? = null,
    @Json(name = "completedSections") val completedSections: Int? = null,
    @Json(name = "totalSections") val totalSections: Int? = null,
    @Json(name = "progressPercent") val progressPercent: Int? = null,
    @Json(name = "isComplete") val isComplete: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SubtitleSeekRequestDto(
    @Json(name = "id") val id: String,
    @Json(name = "currentTime") val currentTime: Double
)

@JsonClass(generateAdapter = true)
data class SubtitleSeekResponseDto(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "mediaId") val mediaId: String? = null,
    @Json(name = "targetSection") val targetSection: Int? = null,
    @Json(name = "isReady") val isReady: Boolean = false,
    @Json(name = "inFlight") val inFlight: Boolean = false
)

