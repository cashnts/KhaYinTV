package dev.khayin.app.core.analytics

import android.app.Application
import android.util.Log
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

object PostHogAnalytics {
    private const val TAG = "PostHogAnalytics"
    const val API_KEY = "phc_BbmKpZksuoFxSHLj5PS8tbZttzcwkFU82AsQdyLiTsrd"
    const val HOST = "https://us.i.posthog.com"

    fun start(application: Application) {
        try {
            val config = PostHogAndroidConfig(
                apiKey = API_KEY,
                host = HOST
            ).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = true
            }
            PostHogAndroid.setup(application, config)
            val savedKey = dev.khayin.app.features.license.LicenseStorage.loadLastKnownKey()?.takeIf { it.isNotBlank() }
            if (savedKey != null) {
                PostHog.identify(savedKey)
            }
            Log.i(TAG, "PostHog initialized successfully on TV (distinctId=$savedKey)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PostHog", e)
        }
    }

    fun capture(
        event: String,
        properties: Map<String, Any>? = null
    ) {
        try {
            val enrichedProps = (properties ?: emptyMap()).toMutableMap()
            enrichedProps["platform"] = "Android TV"
            PostHog.capture(event, properties = enrichedProps)
        } catch (e: Exception) {
            Log.w(TAG, "PostHog capture failed: $event", e)
        }
    }

    fun identify(distinctId: String, userProperties: Map<String, Any>? = null) {
        try {
            PostHog.identify(distinctId, userProperties = userProperties)
        } catch (e: Exception) {
            Log.w(TAG, "PostHog identify failed", e)
        }
    }

    fun log(
        level: String = "INFO",
        tag: String = "App",
        message: String,
        throwable: Throwable? = null,
        properties: Map<String, Any>? = null
    ) {
        try {
            val logProps = (properties ?: emptyMap()).toMutableMap()
            logProps["platform"] = "Android TV"
            logProps["\$level"] = level.lowercase()
            logProps["\$message"] = message
            logProps["tag"] = tag
            if (throwable != null) {
                logProps["error_message"] = throwable.message ?: ""
                logProps["stack_trace"] = throwable.stackTraceToString().take(2000)
            }
            PostHog.capture("\$log", properties = logProps)
            if (level.equals("ERROR", ignoreCase = true) || throwable != null) {
                val exProps = (properties ?: emptyMap()).toMutableMap()
                exProps["platform"] = "Android TV"
                exProps["\$exception_message"] = throwable?.message ?: message
                exProps["\$exception_type"] = throwable?.let { it::class.java.simpleName } ?: "Error"
                exProps["tag"] = tag
                if (throwable != null) {
                    exProps["\$exception_stack_trace_raw"] = throwable.stackTraceToString().take(4000)
                }
                PostHog.capture("\$exception", properties = exProps)
            }
        } catch (e: Exception) {
            Log.w(TAG, "PostHog log failed: $message", e)
        }
    }

    fun captureException(
        throwable: Throwable,
        tag: String = "Error",
        properties: Map<String, Any>? = null
    ) {
        log(
            level = "ERROR",
            tag = tag,
            message = throwable.message ?: "Exception occurred",
            throwable = throwable,
            properties = properties
        )
    }

    fun screen(screenName: String, properties: Map<String, Any>? = null) {
        try {
            val enrichedProps = (properties ?: emptyMap()).toMutableMap()
            enrichedProps["platform"] = "Android TV"
            PostHog.screen(screenName, properties = enrichedProps)
        } catch (e: Exception) {
            Log.w(TAG, "PostHog screen failed: $screenName", e)
        }
    }

    fun trackPlaybackStarted(
        mediaTitle: String,
        contentType: String? = null,
        videoId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        durationMs: Long = 0L,
        positionMs: Long = 0L,
        isP2p: Boolean = false,
        streamName: String? = null,
        addonName: String? = null,
    ) {
        capture(
            event = "playback_started",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (contentType != null) put("content_type", contentType)
                if (videoId != null) put("video_id", videoId)
                if (season != null && season > 0) put("season", season)
                if (episode != null && episode > 0) put("episode", episode)
                put("duration_ms", durationMs)
                put("position_ms", positionMs)
                put("is_p2p", isP2p)
                if (streamName != null) put("stream_name", streamName)
                if (addonName != null) put("addon_name", addonName)
            }
        )
    }

    fun trackPlaybackPaused(
        mediaTitle: String,
        videoId: String? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        progressPercent: Float = 0f,
    ) {
        capture(
            event = "playback_paused",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("position_ms", positionMs)
                put("duration_ms", durationMs)
                put("progress_percent", progressPercent)
            }
        )
    }

    fun trackPlaybackResumed(
        mediaTitle: String,
        videoId: String? = null,
        positionMs: Long = 0L,
    ) {
        capture(
            event = "playback_resumed",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("position_ms", positionMs)
            }
        )
    }

    fun trackPlaybackFinished(
        mediaTitle: String,
        videoId: String? = null,
        durationMs: Long = 0L,
        progressPercent: Float = 100f,
    ) {
        capture(
            event = "playback_finished",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("duration_ms", durationMs)
                put("progress_percent", progressPercent)
                put("completed", true)
            }
        )
    }

    fun trackPlaybackStopped(
        mediaTitle: String,
        videoId: String? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        progressPercent: Float = 0f,
        completed: Boolean = false,
    ) {
        capture(
            event = if (completed) "playback_finished" else "playback_stopped",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("position_ms", positionMs)
                put("duration_ms", durationMs)
                put("progress_percent", progressPercent)
                put("completed", completed)
            }
        )
    }

    fun trackPlaybackFailed(
        mediaTitle: String,
        videoId: String? = null,
        errorMessage: String,
        sourceUrl: String? = null,
    ) {
        capture(
            event = "playback_failed",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("error_message", errorMessage)
                if (sourceUrl != null) put("source_url", sourceUrl.take(300))
            }
        )
    }

    fun trackStreamFetchStarted(
        type: String,
        videoId: String,
        season: Int? = null,
        episode: Int? = null,
        addonCount: Int = 0,
        pluginCount: Int = 0,
    ) {
        capture(
            event = "stream_fetch_started",
            properties = buildMap {
                put("media_type", type)
                put("video_id", videoId)
                if (season != null && season > 0) put("season", season)
                if (episode != null && episode > 0) put("episode", episode)
                put("addon_count", addonCount)
                put("plugin_count", pluginCount)
            }
        )
    }

    fun trackStreamFetchCompleted(
        type: String,
        videoId: String,
        totalStreams: Int,
        groupCount: Int,
        durationMs: Long? = null,
        isEmpty: Boolean = false,
        emptyReason: String? = null,
    ) {
        capture(
            event = "stream_fetch_completed",
            properties = buildMap {
                put("media_type", type)
                put("video_id", videoId)
                put("total_streams", totalStreams)
                put("group_count", groupCount)
                if (durationMs != null) put("duration_ms", durationMs)
                put("is_empty", isEmpty)
                if (emptyReason != null) put("empty_reason", emptyReason)
            }
        )
    }

    fun trackStreamSelected(
        mediaTitle: String,
        videoId: String? = null,
        streamName: String,
        addonName: String? = null,
        resolution: String? = null,
        isDebrid: Boolean = false,
        isP2p: Boolean = false,
    ) {
        capture(
            event = "stream_selected",
            properties = buildMap {
                put("media_title", mediaTitle)
                if (videoId != null) put("video_id", videoId)
                put("stream_name", streamName)
                if (addonName != null) put("addon_name", addonName)
                if (resolution != null) put("resolution", resolution)
                put("is_debrid", isDebrid)
                put("is_p2p", isP2p)
            }
        )
    }

    fun trackSearch(
        query: String,
        totalResults: Int,
        sectionCount: Int = 0,
        hasError: Boolean = false,
    ) {
        capture(
            event = "search_performed",
            properties = mapOf(
                "query" to query,
                "total_results" to totalResults,
                "section_count" to sectionCount,
                "has_error" to hasError
            )
        )
    }

    fun trackAddonInstalled(
        addonName: String,
        addonId: String,
        manifestUrl: String,
    ) {
        capture(
            event = "addon_installed",
            properties = mapOf(
                "addon_name" to addonName,
                "addon_id" to addonId,
                "manifest_url" to manifestUrl
            )
        )
    }

    fun trackAddonUninstalled(
        manifestUrl: String,
    ) {
        capture(
            event = "addon_uninstalled",
            properties = mapOf(
                "manifest_url" to manifestUrl
            )
        )
    }

    fun trackProfileSwitched(
        profileIndex: Int,
        profileName: String,
        isKid: Boolean = false,
    ) {
        capture(
            event = "profile_switched",
            properties = mapOf(
                "profile_index" to profileIndex,
                "profile_name" to profileName,
                "is_kid" to isKid
            )
        )
    }

    fun trackSubtitleError(
        errorType: String,
        errorMessage: String,
        subtitleId: String? = null,
        subtitleUrl: String? = null,
        language: String? = null,
        addonName: String? = null,
        mimeType: String? = null,
        throwable: Throwable? = null,
        extra: Map<String, Any>? = null,
    ) {
        val props = buildMap<String, Any> {
            put("error_type", errorType)
            put("error_message", errorMessage)
            if (subtitleId != null) put("subtitle_id", subtitleId)
            if (subtitleUrl != null) put("subtitle_url", subtitleUrl.take(300))
            if (language != null) put("language", language)
            if (addonName != null) put("addon_name", addonName)
            if (mimeType != null) put("mime_type", mimeType)
            if (extra != null) putAll(extra)
        }
        capture(event = "subtitle_error", properties = props)
        log(
            level = "ERROR",
            tag = "Subtitle",
            message = "Subtitle error [$errorType]: $errorMessage (lang=$language, addon=$addonName, id=$subtitleId)",
            throwable = throwable,
            properties = props
        )
        try {
            if (io.sentry.Sentry.isEnabled()) {
                val sentryEvent = io.sentry.SentryEvent(throwable ?: Exception("SubtitleError[$errorType]: $errorMessage")).apply {
                    level = io.sentry.SentryLevel.ERROR
                    setTag("feature", "subtitle")
                    setTag("subtitle.error_type", errorType)
                    if (language != null) setTag("subtitle.language", language)
                    if (addonName != null) setTag("subtitle.addon", addonName)
                    if (mimeType != null) setTag("subtitle.mime_type", mimeType)
                    setExtra("subtitle_url", subtitleUrl?.take(300) ?: "")
                    setExtra("subtitle_id", subtitleId ?: "")
                    extra?.forEach { (k, v) -> setExtra(k, v) }
                }
                io.sentry.Sentry.captureEvent(sentryEvent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sentry subtitle error capture failed", e)
        }
    }

    fun reset() {
        try {
            PostHog.reset()
        } catch (e: Exception) {
            Log.w(TAG, "PostHog reset failed", e)
        }
    }
}

