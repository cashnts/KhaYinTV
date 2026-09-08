package dev.khayin.app.features.license

import dev.khayin.app.domain.model.AddonStreams
import dev.khayin.app.domain.model.Stream

object FreeTierQualityLimiter {

    /**
     * Filters addon stream groups for Free users on movie content:
     * - Capped to 720p if 720p or lower is available.
     * - Capped to 1080p if 720p is not available.
     */
    fun filterAddonStreamsForFreeTier(
        groups: List<AddonStreams>,
        isMovie: Boolean
    ): List<AddonStreams> {
        if (!LicenseRepository.isFreeUser || !isMovie || groups.isEmpty()) {
            return groups
        }

        val allStreams = groups.flatMap { it.streams }
        val has720pOrBelow = allStreams.any { stream ->
            val res = stream.detectResolutionP()
            res in 1..720
        }
        val maxAllowedRes = if (has720pOrBelow) 720 else 1080

        return groups.mapNotNull { group ->
            val filtered = group.streams.filter { stream ->
                val res = stream.detectResolutionP()
                res <= 0 || res <= maxAllowedRes
            }
            if (filtered.isNotEmpty()) group.copy(streams = filtered) else null
        }
    }

    /**
     * Filters a flat list of streams for Free users on movie content.
     */
    fun filterStreamsForFreeTier(
        streams: List<Stream>,
        isMovie: Boolean
    ): List<Stream> {
        if (!LicenseRepository.isFreeUser || !isMovie || streams.isEmpty()) {
            return streams
        }

        val has720pOrBelow = streams.any { stream ->
            val res = stream.detectResolutionP()
            res in 1..720
        }
        val maxAllowedRes = if (has720pOrBelow) 720 else 1080

        return streams.filter { stream ->
            val res = stream.detectResolutionP()
            res <= 0 || res <= maxAllowedRes
        }
    }
}
