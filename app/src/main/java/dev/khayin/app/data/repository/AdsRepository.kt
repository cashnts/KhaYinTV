package dev.khayin.app.data.repository

import android.util.Log
import dev.khayin.app.data.remote.api.AdsApi
import dev.khayin.app.data.remote.dto.AdItemDto
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AdsRepository"
private const val ADS_TIMEOUT_MS = 8000L

data class ResolvedAd(
    val id: String,
    val title: String,
    val url: String,
    val duration: Int,
    val skippableAfter: Int,
)

sealed interface AdsResult {
    data class Available(val ad: ResolvedAd) : AdsResult
    data object Disabled : AdsResult
    data object Unavailable : AdsResult
}

@Singleton
class AdsRepository @Inject constructor(
    private val adsApi: AdsApi,
) {
    suspend fun getNextAd(): AdsResult {
        dev.khayin.app.core.analytics.PostHogAnalytics.trackAdRequested(
            adType = "preroll",
            isFreeUser = dev.khayin.app.features.license.LicenseRepository.isFreeUser
        )
        return try {
            val response = withTimeoutOrNull(ADS_TIMEOUT_MS) {
                adsApi.getNextAd()
            } ?: run {
                Log.w(TAG, "Ad request timed out after ${ADS_TIMEOUT_MS}ms")
                dev.khayin.app.core.analytics.PostHogAnalytics.trackAdUnavailable("Ad request timed out", adType = "preroll")
                return AdsResult.Unavailable
            }

            if (!response.isSuccessful) {
                Log.w(TAG, "Ad request returned HTTP ${response.code()}")
                dev.khayin.app.core.analytics.PostHogAnalytics.trackAdUnavailable("HTTP ${response.code()}", adType = "preroll")
                return AdsResult.Unavailable
            }

            val body = response.body() ?: run {
                dev.khayin.app.core.analytics.PostHogAnalytics.trackAdUnavailable("Empty response body", adType = "preroll")
                return AdsResult.Unavailable
            }
            if (!body.enabled) {
                Log.d(TAG, "Ads are globally disabled by server")
                dev.khayin.app.core.analytics.PostHogAnalytics.trackAdUnavailable("Disabled by server killswitch", adType = "preroll")
                return AdsResult.Disabled
            }

            val item = body.ad
            if (item != null && !item.url.isNullOrBlank()) {
                val resolved = ResolvedAd(
                    id = item.id?.takeIf { it.isNotBlank() } ?: item.url ?: "preroll",
                    title = item.title?.takeIf { it.isNotBlank() } ?: "KhaYin Spotlight",
                    url = item.url,
                    duration = item.duration ?: 15,
                    skippableAfter = item.skippableAfter ?: 5,
                )
                dev.khayin.app.core.analytics.PostHogAnalytics.trackAdLoaded(
                    adId = resolved.id,
                    adTitle = resolved.title,
                    adUrl = resolved.url,
                    durationSeconds = resolved.duration,
                    skippableAfter = resolved.skippableAfter,
                    adType = "preroll"
                )
                AdsResult.Available(resolved)
            } else {
                dev.khayin.app.core.analytics.PostHogAnalytics.trackAdUnavailable("Missing ad payload or URL", adType = "preroll")
                AdsResult.Unavailable
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve next ad", e)
            dev.khayin.app.core.analytics.PostHogAnalytics.trackAdUnavailable(e.message ?: "Unknown error", adType = "preroll")
            AdsResult.Unavailable
        }
    }
}
