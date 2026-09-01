package dev.khayin.app.core.player

import dev.khayin.app.core.build.AppFeaturePolicy
import dev.khayin.app.data.local.StreamAutoPlayMode
import dev.khayin.app.data.local.StreamAutoPlaySource
import dev.khayin.app.domain.model.AddonStreams
import dev.khayin.app.domain.model.Stream
import dev.khayin.app.domain.model.StreamDebridCacheState

object StreamAutoPlaySelector {
    fun orderAddonStreams(
        streams: List<AddonStreams>,
        installedOrder: List<String>
    ): List<AddonStreams> {
        if (streams.isEmpty()) return streams

        val addonRankByName = HashMap<String, Int>(installedOrder.size)
        installedOrder.forEachIndexed { index, addonName ->
            if (addonName !in addonRankByName) {
                addonRankByName[addonName] = index
            }
        }

        val (directDebridEntries, remainingEntries) = streams.partition {
            it.streams.any { stream -> stream.isDirectDebrid() }
        }
        if (installedOrder.isEmpty()) return directDebridEntries + remainingEntries
        val (addonEntries, pluginEntries) = remainingEntries.partition { it.addonName in addonRankByName }
        val orderedAddons = addonEntries.sortedBy { addonRankByName.getValue(it.addonName) }
        return directDebridEntries + orderedAddons + pluginEntries
    }

    private fun isPlayable(stream: Stream): Boolean {
        // External URL streams (e.g. error pages, web links) are not playable.
        if (stream.isExternal()) return false
        when (stream.debridCacheStatus?.state) {
            StreamDebridCacheState.CHECKING,
            StreamDebridCacheState.NOT_CACHED,
            StreamDebridCacheState.UNKNOWN -> return false
            StreamDebridCacheState.CACHED,
            null -> Unit
        }
        return stream.getStreamUrl() != null || stream.isTorrent() || stream.isDirectDebrid()
    }



    fun selectAutoPlayStream(
        streams: List<Stream>,
        mode: StreamAutoPlayMode,
        regexPattern: String,
        source: StreamAutoPlaySource,
        installedAddonNames: Set<String>,
        selectedAddons: Set<String>,
        selectedPlugins: Set<String>,
        preferredBingeGroup: String? = null,
        preferBingeGroupInSelection: Boolean = false,
        bingeGroupOnly: Boolean = false
    ): Stream? {
        if (streams.isEmpty()) return null

        val effectiveSource = if (!AppFeaturePolicy.pluginsEnabled && source == StreamAutoPlaySource.ENABLED_PLUGINS_ONLY) {
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY
        } else {
            source
        }

        val sourceScopedStreams = when (effectiveSource) {
            StreamAutoPlaySource.ALL_SOURCES -> streams
            StreamAutoPlaySource.INSTALLED_ADDONS_ONLY -> streams.filter { it.addonName in installedAddonNames }
            StreamAutoPlaySource.ENABLED_PLUGINS_ONLY -> streams.filter { it.addonName !in installedAddonNames }
        }
        val candidateStreams = sourceScopedStreams.filter { stream ->
            val isAddonStream = stream.addonName in installedAddonNames
            if (isAddonStream) {
                selectedAddons.isEmpty() || stream.addonName in selectedAddons
            } else {
                selectedPlugins.isEmpty() || stream.addonName in selectedPlugins
            }
        }
        if (candidateStreams.isEmpty()) return null

        // Binge group matching takes priority over mode — even in MANUAL mode,
        // a persisted binge group should auto-play without showing the picker.
        val targetBingeGroup = preferredBingeGroup?.trim().orEmpty()
        if (preferBingeGroupInSelection && targetBingeGroup.isNotEmpty()) {
            val bingeGroupMatch = candidateStreams.firstOrNull { stream ->
                stream.behaviorHints?.bingeGroup == targetBingeGroup && isPlayable(stream)
            }
            if (bingeGroupMatch != null) return bingeGroupMatch
            // When bingeGroupOnly is set (MANUAL mode with only binge-group
            // preference enabled), don't fall back to a non-matching stream —
            // return null so the caller shows the stream picker instead.
            if (bingeGroupOnly) return null
        }

        if (bingeGroupOnly) return null

        if (mode == StreamAutoPlayMode.MANUAL) return null

        return when (mode) {
            StreamAutoPlayMode.MANUAL -> null
            StreamAutoPlayMode.FIRST_STREAM -> candidateStreams.filter { isPlayable(it) }.maxByOrNull { calculateQualityScore(it) }
            StreamAutoPlayMode.REGEX_MATCH -> {
                val pattern = regexPattern.trim()
 
                // Try to compile the user regex
                val userRegex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
                if (userRegex == null) return null

                // Auto-extract exclusion patterns from negative lookaheads
                val exclusionMatches = Regex("\\(\\?![^)]*?\\(([^)]+)\\)").findAll(pattern)

                val exclusionWords = exclusionMatches
                    .flatMap { match -> match.groupValues[1].split("|") }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()

                val excludeRegex = if (exclusionWords.isNotEmpty()) {
                    Regex("\\b(${exclusionWords.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
                } else null

                // 1. Build list of ALL regex‑matching streams
                val matchingStreams = candidateStreams.filter { stream ->
                    if (!isPlayable(stream)) return@filter false

                    val searchableText = buildString {
                        append(stream.addonName).append(' ')
                        append(stream.name.orEmpty()).append(' ')
                        append(stream.title.orEmpty()).append(' ')
                        append(stream.description.orEmpty()).append(' ')
                        append(stream.getStreamUrl().orEmpty())
                        if (stream.isTorrent()) append(' ').append(stream.infoHash.orEmpty())
                    }

                    // Must match include pattern
                    if (!userRegex.containsMatchIn(searchableText)) return@filter false

                    // Must NOT match exclusion pattern
                    if (excludeRegex != null && excludeRegex.containsMatchIn(searchableText)) {
                        return@filter false
                    }

                    true
                }

                if (matchingStreams.isEmpty()) return null
                matchingStreams.maxByOrNull { calculateQualityScore(it) }
            }
        }
    }

    fun calculateQualityScore(stream: Stream): Long {
        var score = 0L
        val text = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.title.orEmpty()).append(' ')
            append(stream.description.orEmpty()).append(' ')
            append(stream.behaviorHints?.filename.orEmpty())
        }.lowercase()

        // 1. Cached vs Uncached
        val isUncached = stream.debridCacheStatus?.state == StreamDebridCacheState.NOT_CACHED ||
            listOf(
                "uncached", "not cached", "not_cached", "non-cached", "[download]", "(download)",
                "downloading", "[dl]", "⏳", "❌", "caching in progress", "[rd download]", "[tb download]"
            ).any { text.contains(it) } || Regex("""(?i)\[(?:rd|ad|pm|tb|torbox)\](?!\+)""").containsMatchIn(text)

        val isConfirmedCached = !isUncached && (
            stream.isDirectDebrid() ||
            stream.debridCacheStatus?.state == StreamDebridCacheState.CACHED ||
            listOf("[rd+]", "[ad+]", "[pm+]", "[tb+]", "[torbox+]", "[debrid+]", "[realdebrid+]", "[cached]", "⚡", "instant", "[ready]").any { text.contains(it) }
        )

        if (isUncached) {
            score -= 10_000_000L
        } else if (isConfirmedCached) {
            score += 1_000_000L
        }

        // 2. Low quality penalty
        if (listOf("camrip", "hdcam", "telesync", "hdts", "screener", "cam-rip", "ts-rip", "hdtc").any { text.contains(it) }) {
            score -= 5_000_000L
        }

        // 3. Direct playable URL
        if (!stream.getStreamUrl().isNullOrBlank()) {
            score += 100_000L
        }

        // 4. Resolution
        when {
            Regex("""(?i)\b(2160p?|4k|uhd)\b""").containsMatchIn(text) -> score += 40_000L
            Regex("""(?i)\b(1440p?|2k|qhd)\b""").containsMatchIn(text) -> score += 30_000L
            Regex("""(?i)\b(1080p?|fhd)\b""").containsMatchIn(text) -> score += 20_000L
            Regex("""(?i)\b(720p?|hd)\b""").containsMatchIn(text) && !text.contains("hdr") && !text.contains("truehd") -> score += 10_000L
        }

        // 5. Codec
        when {
            text.contains("av1") -> score += 15_000L
            text.contains("hevc") || text.contains("x265") || text.contains("h.265") -> score += 12_000L
            text.contains("x264") || text.contains("h.264") -> score += 8_000L
        }

        // 6. Audio
        if (text.contains("atmos") || text.contains("truehd") || text.contains("dts-hd") || text.contains("dts:x")) {
            score += 8_000L
        } else if (text.contains("5.1") || text.contains("7.1")) {
            score += 4_000L
        }

        return score
    }
}
