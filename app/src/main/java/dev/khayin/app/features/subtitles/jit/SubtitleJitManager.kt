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
        private const val STATUS_POLL_INTERVAL_MS = 15_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var pollingJob: Job? = null

    @Volatile
    private var activeSessionId: String? = null
    @Volatile
    private var activeMediaId: String? = null
    @Volatile
    private var activeType: String = "movie"
    @Volatile
    private var activeSubtitleUrl: String? = null

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
        val cleanId = mediaId.trim()
        val sessionKey = "$cleanType:$cleanId:$subtitleUrl"

        if (activeSessionId == sessionKey) {
            return
        }

        stopSession()

        activeSessionId = sessionKey
        activeMediaId = cleanId
        activeType = cleanType
        activeSubtitleUrl = subtitleUrl

        Log.d(TAG, "Starting JIT session for $cleanType $cleanId ($subtitleUrl)")

        // 1. Heartbeat loop (every 12 seconds while playing)
        heartbeatJob = scope.launch {
            while (isActive && activeSessionId == sessionKey) {
                if (lastIsPlaying) {
                    sendHeartbeat(isPlaying = true)
                    wasPlayingSent = true
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }

        // 2. Status check & progressive refetch loop
        pollingJob = scope.launch {
            var lastCompletedSections = -1
            var isFinished = false

            while (isActive && activeSessionId == sessionKey && !isFinished) {
                try {
                    val res = jitApi.getTranslationStatus(cleanId)
                    if (res.isSuccessful && res.body() != null) {
                        val body = res.body()!!
                        val completed = body.completedSections ?: 0
                        Log.d(
                            TAG,
                            "JIT status for $cleanId: complete=${body.isComplete} inFlight=${body.inFlight} " +
                                "sections=$completed/${body.totalSections} progress=${body.progressPercent}%"
                        )

                        if (body.isComplete) {
                            isFinished = true
                            onNewCuesAvailable?.invoke(subtitleUrl)
                            break
                        } else if (completed > lastCompletedSections || lastCompletedSections == -1) {
                            lastCompletedSections = completed
                            onNewCuesAvailable?.invoke(subtitleUrl)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "JIT status check error: ${e.message}")
                }
                delay(STATUS_POLL_INTERVAL_MS)
            }
        }
    }

    fun updatePlaybackProgress(
        currentTimeSec: Double,
        isPlaying: Boolean,
        durationSec: Double
    ) {
        lastCurrentTimeSec = currentTimeSec
        lastDurationSec = durationSec

        if (lastIsPlaying != isPlaying) {
            lastIsPlaying = isPlaying
            if (!isPlaying && wasPlayingSent) {
                // User paused: send a single heartbeat with isPlaying = false immediately
                scope.launch {
                    sendHeartbeat(isPlaying = false)
                }
                wasPlayingSent = false
            }
        }
    }

    private suspend fun sendHeartbeat(isPlaying: Boolean) {
        val mediaId = activeMediaId ?: return
        try {
            val req = SubtitleHeartbeatRequestDto(
                slug = "anonymous",
                type = activeType,
                id = mediaId,
                currentTime = lastCurrentTimeSec,
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

        heartbeatJob?.cancel()
        heartbeatJob = null
        pollingJob?.cancel()
        pollingJob = null

        activeSessionId = null
        activeMediaId = null
        activeSubtitleUrl = null
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
