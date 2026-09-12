package dev.khayin.app.features.subtitles.jit

import android.util.Log
import dev.khayin.app.data.remote.api.SubtitleJitApi
import dev.khayin.app.data.remote.dto.SubtitleHeartbeatRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleJitManager @Inject constructor(
    private val jitApi: SubtitleJitApi
) {
    companion object {
        private const val TAG = "SubtitleJitManager"
        private const val HEARTBEAT_INTERVAL_MS = 12_000L
        // Fast poll while translation is in-flight; stops once complete
        private const val STATUS_POLL_INTERVAL_IN_FLIGHT_MS = 4_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null
    private var seekJob: Job? = null

    @Volatile
    private var activeSessionId: String? = null
    @Volatile
    private var activeMediaId: String? = null
    @Volatile
    private var activeType: String = "movie"
    @Volatile
    private var activeSubtitleUrl: String? = null
    @Volatile
    private var currentOnNewCuesAvailable: (suspend (url: String) -> Unit)? = null
    @Volatile
    private var lastLoadedCompletedSections: Int = -1

    @Volatile
    private var lastCurrentTimeSec: Double = 0.0
    @Volatile
    private var lastDurationSec: Double = 0.0
    @Volatile
    private var lastIsPlaying: Boolean = false
    @Volatile
    private var wasPlayingSent: Boolean = false

    fun isJitSubtitle(url: String?, addonName: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return url.contains("stream.khayin.net", ignoreCase = true) ||
            addonName?.contains("KhaYin", ignoreCase = true) == true
    }

    /**
     * Extract the real media/episode ID from the subtitle URL.
     * e.g. https://stream.khayin.net/subtitles/vtt/series/tt1234567:1:1.vtt?extra=...
     * -> "tt1234567:1:1"
     * Falls back to [mediaId] if the URL filename cannot be parsed.
     */
    private fun extractEffectiveId(subtitleUrl: String, mediaId: String): String {
        return try {
            subtitleUrl
                .substringBefore("?")
                .substringAfterLast("/")
                .removeSuffix(".vtt")
                .removeSuffix(".srt")
                .removeSuffix(".json")
                .takeIf { it.isNotBlank() } ?: mediaId
        } catch (_: Exception) {
            mediaId
        }
    }

    fun startSession(
        mediaId: String,
        type: String,
        subtitleUrl: String,
        onNewCuesAvailable: (suspend (url: String) -> Unit)? = null
    ) {
        val cleanType = if (type.equals("series", ignoreCase = true) || type.equals("tv", ignoreCase = true)) {
            "series"
        } else {
            "movie"
        }
        // Use the ID embedded in the subtitle URL (e.g. tt1234567:1:1 for an episode)
        val effectiveId = extractEffectiveId(subtitleUrl, mediaId.trim())
        val sessionKey = "$cleanType:$effectiveId:$subtitleUrl"

        if (activeSessionId == sessionKey) {
            currentOnNewCuesAvailable = onNewCuesAvailable
            return
        }

        stopSession()

        activeSessionId = sessionKey
        activeMediaId = effectiveId
        activeType = cleanType
        activeSubtitleUrl = subtitleUrl
        currentOnNewCuesAvailable = onNewCuesAvailable
        lastLoadedCompletedSections = -1

        Log.d(TAG, "Starting JIT session for $cleanType $effectiveId ($subtitleUrl)")

        // 1. Send an immediate heartbeat so the backend starts/continues translation right away
        scope.launch {
            sendHeartbeat(isPlaying = true)
            wasPlayingSent = true
            lastIsPlaying = true
        }

        // 2. Heartbeat loop (every 12 seconds while playing). First beat already sent above.
        heartbeatJob = scope.launch {
            delay(HEARTBEAT_INTERVAL_MS)
            while (isActive && activeSessionId == sessionKey) {
                if (lastIsPlaying) {
                    sendHeartbeat(isPlaying = true)
                    wasPlayingSent = true
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        // 3. Status check & progressive refetch loop
        ensurePollingActive(effectiveId, sessionKey)
    }

    private fun ensurePollingActive(effectiveId: String, sessionKey: String) {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            while (isActive && activeSessionId == sessionKey) {
                try {
                    val res = jitApi.getTranslationStatus(effectiveId)
                    if (res.isSuccessful && res.body() != null) {
                        val body = res.body()!!
                        val completed = body.completedSections ?: 0
                        val total = body.totalSections ?: 1
                        Log.d(
                            TAG,
                            "JIT status for $effectiveId: complete=${body.isComplete} inFlight=${body.inFlight} " +
                                "sections=$completed/$total progress=${body.progressPercent}%"
                        )

                        if (completed > lastLoadedCompletedSections || lastLoadedCompletedSections == -1) {
                            lastLoadedCompletedSections = completed
                            Log.i(TAG, "New translated section available ($completed/$total) for $effectiveId, triggering reload")
                            activeSubtitleUrl?.let { url -> currentOnNewCuesAvailable?.invoke(url) }
                        }

                        // Stop polling only when all sections are fully completed and no longer in-flight
                        if (body.isComplete && completed >= total && body.inFlight != true) {
                            Log.i(TAG, "JIT translation fully complete ($completed/$total) for $effectiveId")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "JIT status check error: ${e.message}")
                }
                delay(STATUS_POLL_INTERVAL_IN_FLIGHT_MS)
            }
        }
    }

    fun updatePlaybackProgress(
        currentTimeSec: Double,
        isPlaying: Boolean,
        durationSec: Double,
        isLoading: Boolean = false
    ) {
        val prevTime = lastCurrentTimeSec
        lastCurrentTimeSec = currentTimeSec
        lastDurationSec = durationSec

        // If media is still loading or buffering, do not misinterpret as user pausing
        if (isLoading) {
            return
        }

        // Seek detection: if currentTime jumped by more than 8 seconds
        val timeJump = kotlin.math.abs(currentTimeSec - prevTime)
        val isSeek = prevTime > 0.0 && timeJump > 8.0

        if (isSeek && activeMediaId != null) {
            Log.i(TAG, "Playback seek detected: ${prevTime.toLong()}s -> ${currentTimeSec.toLong()}s (jump=${timeJump.toLong()}s). Prioritizing JIT translation.")
            handlePlaybackSeek(currentTimeSec, isPlaying)
        }

        if (lastIsPlaying != isPlaying) {
            lastIsPlaying = isPlaying
            if (!isPlaying && wasPlayingSent) {
                // User intentionally paused: send a single heartbeat with isPlaying = false immediately
                scope.launch {
                    sendHeartbeat(isPlaying = false)
                }
                wasPlayingSent = false
            }
        }
    }

    private fun handlePlaybackSeek(seekTimeSec: Double, isPlaying: Boolean) {
        val mediaId = activeMediaId ?: return
        val sessionKey = activeSessionId ?: return

        seekJob?.cancel()
        seekJob = scope.launch {
            // 1. Immediately send heartbeat with the seek position so backend session updates right away
            sendHeartbeat(isPlaying = isPlaying, currentTime = seekTimeSec)

            // 2. Call /api/translation/seek to prioritize the target section on the backend
            try {
                val seekReq = dev.khayin.app.data.remote.dto.SubtitleSeekRequestDto(
                    id = mediaId,
                    currentTime = seekTimeSec
                )
                val res = jitApi.seekTranslation(seekReq)
                if (res.isSuccessful && res.body() != null) {
                    val seekRes = res.body()!!
                    Log.i(TAG, "Seek API result for $mediaId at ${seekTimeSec.toLong()}s: section=${seekRes.targetSection}, isReady=${seekRes.isReady}")

                    if (seekRes.isReady) {
                        Log.i(TAG, "Target section for seek is already ready! Reloading subtitle immediately.")
                        activeSubtitleUrl?.let { url -> currentOnNewCuesAvailable?.invoke(url) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to notify seek API: ${e.message}")
            }

            // 3. Ensure status polling is active to catch new section cues as soon as translation finishes
            ensurePollingActive(mediaId, sessionKey)
        }
    }

    private suspend fun sendHeartbeat(isPlaying: Boolean, currentTime: Double = lastCurrentTimeSec) {
        val mediaId = activeMediaId ?: return
        try {
            val req = SubtitleHeartbeatRequestDto(
                slug = "anonymous",
                type = activeType,
                id = mediaId,
                currentTime = currentTime,
                isPlaying = isPlaying,
                duration = if (lastDurationSec > 0.0) lastDurationSec else null
            )
            jitApi.sendHeartbeat(req)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send heartbeat: ${e.message}")
        }
    }

    fun stopSession() {
        if (activeSessionId == null) return
        Log.d(TAG, "Stopping JIT session: $activeSessionId")

        val hadActiveHeartbeat = wasPlayingSent
        val prevMediaId = activeMediaId
        val prevType = activeType
        val prevCurrentTime = lastCurrentTimeSec
        val prevDuration = lastDurationSec

        seekJob?.cancel()
        seekJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        pollingJob?.cancel()
        pollingJob = null

        activeSessionId = null
        activeMediaId = null
        activeSubtitleUrl = null
        currentOnNewCuesAvailable = null
        wasPlayingSent = false

        if (hadActiveHeartbeat && prevMediaId != null) {
            scope.launch {
                try {
                    jitApi.sendHeartbeat(
                        SubtitleHeartbeatRequestDto(
                            slug = "anonymous",
                            type = prevType,
                            id = prevMediaId,
                            currentTime = prevCurrentTime,
                            isPlaying = false,
                            duration = if (prevDuration > 0.0) prevDuration else null
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
