package dev.khayin.app.ui.screens.player

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import dev.khayin.app.R
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

internal fun PlayerRuntimeController.attachHostActivity(activity: Activity?) {
    hostActivityRef = activity?.let { WeakReference(it) }
}

internal fun PlayerRuntimeController.startInitialPlaybackIfNeeded() {
    if (initialPlaybackStarted) return

    initialPlaybackStarted = true

    // Persist binge group from navigation args so that subsequent plays
    // (from CW, Details, or next-episode) can reuse the same source group.
    val bg = navigationArgs.bingeGroup
    val cid = contentId
    if (cid != null) {
        scope.launch(kotlinx.coroutines.NonCancellable) {
            bingeGroupCacheDataStore.replace(cid, bg)
        }
    }

    val infoHash = navigationArgs.infoHash
    val clickElapsedMs = launchStartedAtElapsedMs
        ?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
        ?: -1L
    queuePlaybackRawEventLine(
        "PLAYER_START_REQUEST: clickElapsedMs=$clickElapsedMs host=${initialStreamUrl.safeStartupHost()} " +
            "contentId=${contentId ?: "n/a"} videoId=${currentVideoId ?: "n/a"} " +
            "S${currentSeason ?: "-"}E${currentEpisode ?: "-"} infoHash=${infoHash != null} " +
            "startFromBeginning=${navigationArgs.startFromBeginning} streamName=${streamName ?: "n/a"}"
    )
    Log.d(
        "PlayerStartup",
        "startInitialPlayback: infoHash=$infoHash host=${currentStreamUrl.safeStartupHost()} " +
            "urlHash=${currentStreamUrl.hashCode().toUInt().toString(16)}"
    )
    val shouldPlayPreroll = !dev.khayin.app.features.license.LicenseRepository.hasAdFreeAccess &&
        !navigationArgs.prerollUrl.isNullOrBlank()

    if (shouldPlayPreroll) {
        startPrerollSequence()
    } else {
        startMainMoviePlayback()
    }
}

internal fun PlayerRuntimeController.startPrerollSequence() {
    val prerollUrl = navigationArgs.prerollUrl ?: run {
        startMainMoviePlayback()
        return
    }

    isPrerollActive = true
    val prerollTitle = navigationArgs.prerollTitle?.takeIf { it.isNotBlank() } ?: "KhaYin TV"
    val prerollNotice = ""
    val skippableAfter = (navigationArgs.prerollSkippableAfter ?: 5).coerceAtLeast(0)

    _uiState.update {
        it.copy(
            isPrerollActive = true,
            prerollTitle = prerollTitle,
            prerollNotice = prerollNotice,
            canSkipPreroll = skippableAfter == 0,
            prerollSkippableAfter = skippableAfter
        )
    }

    prerollSkipJob?.cancel()
    prerollSkipJob = scope.launch {
        if (skippableAfter > 0) {
            kotlinx.coroutines.delay(skippableAfter * 1000L)
        }
        _uiState.update { it.copy(canSkipPreroll = true) }
    }

    dev.khayin.app.core.analytics.PostHogAnalytics.trackAdStarted(
        adId = navigationArgs.prerollId,
        adTitle = prerollTitle,
        adUrl = prerollUrl,
        durationSeconds = navigationArgs.prerollDuration ?: 15,
        skippableAfter = skippableAfter,
        mediaTitle = navigationArgs.title,
        videoId = navigationArgs.videoId
    )

    preparePlaybackBeforeStart(
        url = prerollUrl,
        headers = emptyMap(),
        loadSavedProgress = false
    )
}

internal fun PlayerRuntimeController.finishPrerollAndStartMainMovie() {
    if (!isPrerollActive) return
    isPrerollActive = false
    prerollSkipJob?.cancel()
    prerollSkipJob = null

    _uiState.update {
        it.copy(
            isPrerollActive = false,
            prerollTitle = null,
            prerollNotice = null,
            canSkipPreroll = false
        )
    }

    startMainMoviePlayback()
}

internal fun PlayerRuntimeController.startMainMoviePlayback() {
    val infoHash = navigationArgs.infoHash
    if (infoHash != null && !initialStreamUrl.startsWith("http")) {
        torrentStreamJob = scope.launch {
            try {
                Log.d("PlayerStartup", "Starting torrent stream for $infoHash")
                observeTorrentState()
                val localUrl = startTorrentStream(
                    infoHash = infoHash,
                    fileIdx = navigationArgs.fileIdx,
                    filename = navigationArgs.filename,
                    trackers = navigationArgs.torrentTrackers
                )
                Log.d("PlayerStartup", "Torrent stream ready: $localUrl")
                currentStreamUrl = localUrl
                currentHeaders = emptyMap()
                // Use loadSavedProgress = true — TorrServer handles seeking via
                // HTTP Range requests, so ExoPlayer's standard resume logic works.
                preparePlaybackBeforeStart(
                    url = localUrl,
                    headers = emptyMap(),
                    loadSavedProgress = true
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("PlayerStartup", "Failed to start torrent", e)
                _uiState.update {
                    it.copy(
                        error = context.getString(
                            R.string.player_error_failed_start_torrent,
                            e.message ?: context.getString(R.string.error_unknown)
                        ),
                        showLoadingOverlay = false
                    )
                }
            }
        }
        return
    }

    preparePlaybackBeforeStart(
        url = mainMovieStreamUrl,
        headers = mainMovieHeaders,
        loadSavedProgress = !navigationArgs.startFromBeginning
    )
}

internal fun PlayerRuntimeController.currentHostActivity(): Activity? {
    return hostActivityRef?.get()
}

private fun String.safeStartupHost(): String {
    return runCatching {
        android.net.Uri.parse(this).host ?: substringBefore("://").takeIf { it.isNotBlank() } ?: "unknown"
    }.getOrDefault("unknown")
}
