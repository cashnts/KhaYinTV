package dev.khayin.app.features.license

import dev.khayin.app.domain.model.AddonStreams
import dev.khayin.app.domain.model.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FreeTierQualityLimiterTest {

    @Before
    fun setup() {
        LicenseRepository.continueForFree()
    }

    private fun createStream(name: String, quality: String): Stream {
        return Stream(
            name = name,
            title = quality,
            url = "https://example.com/video.mp4"
        )
    }

    @Test
    fun filterStreamsForFreeTier_when720pExists_capsTo720p() {
        val s4k = createStream("4K", "2160p UHD")
        val s1080 = createStream("1080p", "1080p FHD")
        val s720 = createStream("720p", "720p HD")
        val s480 = createStream("480p", "480p SD")

        val streams = listOf(s4k, s1080, s720, s480)
        val filtered = FreeTierQualityLimiter.filterStreamsForFreeTier(streams, isMovie = true)

        assertEquals(2, filtered.size)
        assertTrue(filtered.contains(s720))
        assertTrue(filtered.contains(s480))
    }

    @Test
    fun filterStreamsForFreeTier_whenNo720pExists_capsTo1080p() {
        val s4k = createStream("4K", "2160p UHD")
        val s1080 = createStream("1080p", "1080p FHD")

        val streams = listOf(s4k, s1080)
        val filtered = FreeTierQualityLimiter.filterStreamsForFreeTier(streams, isMovie = true)

        assertEquals(1, filtered.size)
        assertTrue(filtered.contains(s1080))
    }

    @Test
    fun filterStreamsForFreeTier_whenSeriesContent_doesNotCap() {
        val s4k = createStream("4K", "2160p UHD")
        val s1080 = createStream("1080p", "1080p FHD")
        val s720 = createStream("720p", "720p HD")

        val streams = listOf(s4k, s1080, s720)
        val filtered = FreeTierQualityLimiter.filterStreamsForFreeTier(streams, isMovie = false)

        assertEquals(3, filtered.size)
    }

    @Test
    fun filterAddonStreamsForFreeTier_filtersNestedStreamsCorrectly() {
        val s4k = createStream("4K", "2160p UHD")
        val s1080 = createStream("1080p", "1080p FHD")
        val s720 = createStream("720p", "720p HD")

        val group1 = AddonStreams(addonName = "Addon1", addonLogo = null, streams = listOf(s4k, s1080))
        val group2 = AddonStreams(addonName = "Addon2", addonLogo = null, streams = listOf(s720))

        val filteredGroups = FreeTierQualityLimiter.filterAddonStreamsForFreeTier(listOf(group1, group2), isMovie = true)

        // group1 has only >720p, so it should be dropped because 720p is available in group2
        assertEquals(1, filteredGroups.size)
        assertEquals("Addon2", filteredGroups[0].addonName)
        assertEquals(1, filteredGroups[0].streams.size)
    }
}
