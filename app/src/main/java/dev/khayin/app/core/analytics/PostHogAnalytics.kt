package dev.khayin.app.core.analytics

import android.app.Application
import android.util.Log
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dev.khayin.app.features.license.LicenseStorage

object PostHogAnalytics {
    private const val TAG = "PostHogAnalytics"
    const val API_KEY = "phc_BbmKpZksuoFxSHLj5PS8tbZttzcwkFU82AsQdyLiTsrd"
    const val HOST = "https://us.i.posthog.com"

    private var uncaughtHandlerInstalled = false

    fun start(application: Application) {
        try {
            DeviceDetector.init(application)
            val deviceInfo = DeviceDetector.getDeviceInfo(application)
            val config = PostHogAndroidConfig(
                apiKey = API_KEY,
                host = HOST
            ).apply {
                captureApplicationLifecycleEvents = true
                captureScreenViews = true
                captureDeepLinks = true
                sessionReplay = true
                sessionReplayConfig.maskAllTextInputs = true
                sessionReplayConfig.maskAllImages = true
                sessionReplayConfig.captureLogcat = true
            }
            PostHogAndroid.setup(application, config)
            val savedKey = LicenseStorage.loadLastKnownKey()?.takeIf { it.isNotBlank() }
            if (savedKey != null) {
                PostHog.identify(savedKey)
            }

            PostHogLogger.start()
            PostHogTracer.start()
            installUncaughtExceptionHandler()

            Log.i(TAG, "PostHog initialized with Error Tracking, Tracing & Session Replay on ${deviceInfo.platform} (${deviceInfo.deviceType}, distinctId=$savedKey)")
            log(level = "INFO", tag = "AppLifecycle", message = "KhaYin ${deviceInfo.platform} started (type=${deviceInfo.deviceType}, distinctId=$savedKey)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PostHog", e)
        }
    }

    private fun installUncaughtExceptionHandler() {
        if (uncaughtHandlerInstalled) return
        uncaughtHandlerInstalled = true
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                captureException(
                    throwable = throwable,
                    tag = "UncaughtCrash",
                    isUnhandled = true,
                    properties = mapOf("thread_name" to thread.name)
                )
                PostHog.flush()
                PostHogLogger.flush()
                PostHogTracer.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error logging uncaught exception to PostHog", e)
            } finally {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun capture(
        event: String,
        properties: Map<String, Any>? = null
    ) {
        try {
            val deviceInfo = DeviceDetector.getDeviceInfo()
            val enrichedProps = (properties ?: emptyMap()).toMutableMap()
            enrichedProps["platform"] = deviceInfo.platform
            enrichedProps["device_type"] = deviceInfo.deviceType
            enrichedProps["os_name"] = deviceInfo.osName
            enrichedProps["os_version"] = deviceInfo.osVersion
            enrichedProps["device_model"] = deviceInfo.model
            enrichedProps["device_brand"] = deviceInfo.brand
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

    fun d(tag: String, message: String, properties: Map<String, Any>? = null) {
        log(level = "DEBUG", tag = tag, message = message, properties = properties)
    }

    fun i(tag: String, message: String, properties: Map<String, Any>? = null) {
        log(level = "INFO", tag = tag, message = message, properties = properties)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null, properties: Map<String, Any>? = null) {
        log(level = "WARN", tag = tag, message = message, throwable = throwable, properties = properties)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null, properties: Map<String, Any>? = null) {
        log(level = "ERROR", tag = tag, message = message, throwable = throwable, properties = properties)
    }

    fun log(
        level: String = "INFO",
        tag: String = "App",
        message: String,
        throwable: Throwable? = null,
        properties: Map<String, Any>? = null
    ) {
        try {
            // Forward to PostHog Logs (OTLP)
            PostHogLogger.log(
                level = level,
                tag = tag,
                message = message,
                throwable = throwable,
                attributes = properties
            )

            // If error/fatal, also report into PostHog Error Tracking ($exception)
            if (level.equals("ERROR", ignoreCase = true) || level.equals("FATAL", ignoreCase = true) || throwable != null) {
                val deviceInfo = DeviceDetector.getDeviceInfo()
                val exProps = (properties ?: emptyMap()).toMutableMap()
                exProps["platform"] = deviceInfo.platform
                exProps["device_type"] = deviceInfo.deviceType
                exProps["\$exception_message"] = throwable?.message ?: message
                exProps["\$exception_type"] = throwable?.let { it::class.java.name } ?: "ApplicationError"
                exProps["tag"] = tag
                exProps["\$exception_handled"] = !level.equals("FATAL", ignoreCase = true)
                if (throwable != null) {
                    exProps["\$exception_stack_trace_raw"] = throwable.stackTraceToString().take(6000)
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
        isUnhandled: Boolean = false,
        properties: Map<String, Any>? = null
    ) {
        try {
            val deviceInfo = DeviceDetector.getDeviceInfo()
            val exProps = (properties ?: emptyMap()).toMutableMap()
            exProps["platform"] = deviceInfo.platform
            exProps["device_type"] = deviceInfo.deviceType
            exProps["tag"] = tag
            exProps["\$exception_type"] = throwable.javaClass.name
            exProps["\$exception_message"] = throwable.message ?: throwable.javaClass.simpleName
            exProps["\$exception_stack_trace_raw"] = throwable.stackTraceToString().take(8000)
            exProps["\$exception_handled"] = !isUnhandled
            PostHog.capture("\$exception", properties = exProps)
        } catch (e: Exception) {
            Log.w(TAG, "PostHog captureException failed", e)
        }

        PostHogLogger.log(
            level = if (isUnhandled) "FATAL" else "ERROR",
            tag = tag,
            message = "${throwable.javaClass.simpleName}: ${throwable.message}",
            throwable = throwable,
            attributes = properties
        )
    }

    fun screen(screenName: String, properties: Map<String, Any>? = null) {
        try {
            val deviceInfo = DeviceDetector.getDeviceInfo()
            val enrichedProps = (properties ?: emptyMap()).toMutableMap()
            enrichedProps["platform"] = deviceInfo.platform
            enrichedProps["device_type"] = deviceInfo.deviceType
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
        log(
            level = "ERROR",
            tag = "Player",
            message = "Playback failed for '$mediaTitle': $errorMessage",
            properties = mapOf("video_id" to (videoId ?: ""), "source_url" to (sourceUrl ?: ""))
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
    }

    fun reset() {
        try {
            PostHog.reset()
        } catch (e: Exception) {
            Log.w(TAG, "PostHog reset failed", e)
        }
    }
}
