package dev.khayin.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AdsNextResponseDto(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "mode") val mode: String? = null,
    @Json(name = "ad") val ad: AdItemDto? = null
)

@JsonClass(generateAdapter = true)
data class AdItemDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "duration") val duration: Int? = null,
    @Json(name = "skippableAfter") val skippableAfter: Int? = null,
    @Json(name = "index") val index: Int? = null
)
