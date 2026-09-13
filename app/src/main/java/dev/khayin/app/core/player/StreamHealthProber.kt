package dev.khayin.app.core.player

import dev.khayin.app.domain.model.Stream
import dev.khayin.app.domain.model.isConfirmedCached
import dev.khayin.app.domain.model.isLowQualitySource
import dev.khayin.app.domain.model.isUncachedStream
import dev.khayin.app.ui.screens.player.PlayerPlaybackNetworking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import kotlin.time.Duration.Companion.milliseconds

data class StreamProbeResult(
    val stream: Stream,
    val isLive: Boolean,
    val latencyMs: Long,
    val httpStatus: Int
)

object StreamHealthProber {
    private val client by lazy {
        PlayerPlaybackNetworking.playbackHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun probeStream(stream: Stream, timeoutMs: Long = 1800L): StreamProbeResult = withContext(Dispatchers.IO) {
        if (stream.isUncachedStream) {
            return@withContext StreamProbeResult(stream, false, Long.MAX_VALUE, 404)
        }

        val url = stream.getStreamUrl() ?: stream.url
        if (url == null) {
            return@withContext StreamProbeResult(stream, false, Long.MAX_VALUE, -1)
        }

        if (url.contains("torrent_not_downloaded", ignoreCase = true) ||
            url.contains("exceptions/", ignoreCase = true) ||
            url.contains("uncached", ignoreCase = true) ||
            url.contains("not_cached", ignoreCase = true) ||
            url.contains("caching_in_progress", ignoreCase = true) ||
            url.contains("static/exceptions", ignoreCase = true)
        ) {
            return@withContext StreamProbeResult(stream, false, Long.MAX_VALUE, 404)
        }

        if (url.startsWith("magnet:", ignoreCase = true) || url.startsWith("torrent:", ignoreCase = true)) {
            val isCached = stream.isDirectDebrid() || stream.isConfirmedCached
            return@withContext StreamProbeResult(
                stream = stream,
                isLive = isCached,
                latencyMs = if (isCached) 150L else 5000L,
                httpStatus = if (isCached) 200 else -1
            )
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=0-1024")

        stream.behaviorHints?.proxyHeaders?.request?.forEach { (k, v) ->
            requestBuilder.addHeader(k, v)
        }

        val startTime = System.currentTimeMillis()
        var responseStatus = -1
        var isLive = false

        try {
            withTimeoutOrNull(timeoutMs) {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    responseStatus = response.code
                    val finalUrl = response.request.url.toString()
                    val location = response.header("Location")
                    val disposition = response.header("Content-Disposition")
                    val contentType = response.header("Content-Type")

                    val buffer = ByteArray(512)
                    val bytesRead = response.body?.byteStream()?.read(buffer) ?: 0
                    val bodyPreview = if (bytesRead > 0) String(buffer, 0, bytesRead, Charsets.UTF_8).lowercase() else ""

                    val isUncachedNotice = finalUrl.contains("torrent_not_downloaded", ignoreCase = true) ||
                        finalUrl.contains("exceptions/", ignoreCase = true) ||
                        finalUrl.contains("uncached", ignoreCase = true) ||
                        finalUrl.contains("not_cached", ignoreCase = true) ||
                        finalUrl.contains("caching_in_progress", ignoreCase = true) ||
                        finalUrl.contains("download_in_progress", ignoreCase = true) ||
                        finalUrl.contains("downloading_", ignoreCase = true) ||
                        finalUrl.contains("playback_error", ignoreCase = true) ||
                        finalUrl.contains("static/exceptions", ignoreCase = true) ||
                        (location?.contains("torrent_not_downloaded", ignoreCase = true) == true) ||
                        (location?.contains("exceptions/", ignoreCase = true) == true) ||
                        (location?.contains("uncached", ignoreCase = true) == true) ||
                        (location?.contains("not_cached", ignoreCase = true) == true) ||
                        (location?.contains("caching_in_progress", ignoreCase = true) == true) ||
                        (location?.contains("downloading", ignoreCase = true) == true) ||
                        (disposition?.contains("torrent_not_downloaded", ignoreCase = true) == true) ||
                        (disposition?.contains("exceptions/", ignoreCase = true) == true) ||
                        bodyPreview.contains("torrent_not_downloaded") ||
                        bodyPreview.contains("caching in progress") ||
                        bodyPreview.contains("cache in progress") ||
                        bodyPreview.contains("not cached") ||
                        bodyPreview.contains("uncached") ||
                        (contentType?.contains("text/html", ignoreCase = true) == true &&
                            (bodyPreview.contains("error") || bodyPreview.contains("exception") || bodyPreview.contains("not downloaded")))

                    isLive = !isUncachedNotice && (response.isSuccessful || response.code in 200..399)
                }
            }
        } catch (_: Throwable) {
            isLive = false
        }

        val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        StreamProbeResult(
            stream = stream,
            isLive = isLive,
            latencyMs = if (isLive) latency else Long.MAX_VALUE,
            httpStatus = responseStatus
        )
    }

    suspend fun findFastestLivingStream(
        candidates: List<Stream>,
        timeoutMs: Long = 1800L
    ): Stream? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null
        val cachedCandidates = candidates.filter { !it.isUncachedStream }
        val streamPool = if (cachedCandidates.isNotEmpty()) cachedCandidates else candidates
        if (streamPool.size == 1) return@coroutineScope streamPool.first()

        val probePool = streamPool.take(25)
        val channel = Channel<StreamProbeResult>(capacity = probePool.size)

        val probeJobs = probePool.map { stream ->
            launch {
                val result = probeStream(stream, timeoutMs)
                channel.send(result)
            }
        }

        var bestStream: Stream? = null
        var bestScore: Long = Long.MIN_VALUE
        val startTime = System.currentTimeMillis()
        var receivedCount = 0

        while (receivedCount < probePool.size) {
            val remainingMs = timeoutMs - (System.currentTimeMillis() - startTime)
            if (remainingMs <= 0 && bestStream != null) {
                break
            }
            val result = withTimeoutOrNull(remainingMs.coerceAtLeast(50L).milliseconds) {
                channel.receiveCatching().getOrNull()
            } ?: break

            receivedCount++
            if (result.isLive && !result.stream.isLowQualitySource && !result.stream.isUncachedStream) {
                val baseScore = StreamAutoPlaySelector.calculateQualityScore(result.stream)
                val latencyPenalty = (result.latencyMs * 5L).coerceAtMost(50_000L)
                val adjustedScore = baseScore - latencyPenalty

                // Instant match check: confirmed cached 4K / 2K / 1080p
                val res = result.stream.detectResolutionP()
                val isHighRes = res >= 1080
                if (result.stream.isConfirmedCached && isHighRes) {
                    probeJobs.forEach { it.cancel() }
                    return@coroutineScope result.stream
                }

                if (adjustedScore > bestScore) {
                    bestStream = result.stream
                    bestScore = adjustedScore
                }
            }
        }

        probeJobs.forEach { it.cancel() }
        bestStream
            ?: streamPool.firstOrNull { it.isConfirmedCached }
            ?: streamPool.firstOrNull { !it.isUncachedStream && !it.isLowQualitySource }
            ?: streamPool.firstOrNull()
    }
}
