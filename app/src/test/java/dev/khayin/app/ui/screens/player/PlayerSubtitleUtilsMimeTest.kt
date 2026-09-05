package dev.khayin.app.ui.screens.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSubtitleUtilsMimeTest {

    @Test
    fun mimeTypeFromUrl_detectsCommonExtensions() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, PlayerSubtitleUtils.mimeTypeFromUrl("https://x/a.srt"))
        assertEquals(MimeTypes.TEXT_VTT, PlayerSubtitleUtils.mimeTypeFromUrl("https://x/a.vtt?token=1"))
        assertEquals(MimeTypes.TEXT_SSA, PlayerSubtitleUtils.mimeTypeFromUrl("https://x/a.ass"))
        assertEquals(MimeTypes.APPLICATION_TTML, PlayerSubtitleUtils.mimeTypeFromUrl("https://x/a.ttml"))
        // Extension-less addon download links default to SRT.
        assertEquals(
            MimeTypes.APPLICATION_SUBRIP,
            PlayerSubtitleUtils.mimeTypeFromUrl("https://opensubtitles.example/download/12345")
        )
    }

    @Test
    fun sniffSubtitleMimeType_detectsWebVttHeader() {
        val body = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi"
        assertEquals(
            MimeTypes.TEXT_VTT,
            PlayerSubtitleUtils.sniffSubtitleMimeType(body, "https://x/download/1")
        )
    }

    @Test
    fun sniffSubtitleMimeType_detectsAssScriptInfo() {
        val body = """
            [Script Info]
            Title: test
            [V4+ Styles]
            Format: Name
            [Events]
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,Hello
        """.trimIndent()
        assertEquals(
            MimeTypes.TEXT_SSA,
            PlayerSubtitleUtils.sniffSubtitleMimeType(body, "https://x/download/1")
        )
    }

    @Test
    fun sniffSubtitleMimeType_detectsSrtTiming() {
        val body = """
            1
            00:00:01,000 --> 00:00:02,000
            Hello
        """.trimIndent()
        assertEquals(
            MimeTypes.APPLICATION_SUBRIP,
            PlayerSubtitleUtils.sniffSubtitleMimeType(body, "https://x/download/1")
        )
    }

    @Test
    fun sidecarMimeCandidates_putsSniffedFirstAndDedupes() {
        val body = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi"
        val candidates = PlayerSubtitleUtils.sidecarMimeCandidates(body, "https://x/file.srt")
        assertEquals(MimeTypes.TEXT_VTT, candidates.first())
        assertTrue(candidates.contains(MimeTypes.APPLICATION_SUBRIP))
        assertEquals(candidates.size, candidates.distinct().size)
    }

    @Test
    fun mergeOverlappingCues_mergesMultipleUnpositionedCues() {
        val cue1 = androidx.media3.common.text.Cue.Builder().setText(">> (sputtering)").build()
        val cue2 = androidx.media3.common.text.Cue.Builder().setText(">> HEY!").build()
        val merged = PlayerSubtitleUtils.mergeOverlappingCues(listOf(cue1, cue2))
        assertEquals(1, merged.size)
        assertEquals(">> (sputtering)\n>> HEY!", merged[0].text.toString())
    }

    @Test
    fun mergeOverlappingCues_preservesPositionedCues() {
        val cue1 = androidx.media3.common.text.Cue.Builder().setText("TOP").setLine(0.1f, androidx.media3.common.text.Cue.LINE_TYPE_FRACTION).build()
        val cue2 = androidx.media3.common.text.Cue.Builder().setText("BOTTOM").setLine(0.9f, androidx.media3.common.text.Cue.LINE_TYPE_FRACTION).build()
        val result = PlayerSubtitleUtils.mergeOverlappingCues(listOf(cue1, cue2))
        assertEquals(2, result.size)
        assertEquals("TOP", result[0].text.toString())
        assertEquals("BOTTOM", result[1].text.toString())
    }

    @Test
    fun mergeOverlappingCues_singleCue_returnsSame() {
        val list = listOf(androidx.media3.common.text.Cue.Builder().setText("Single").build())
        org.junit.Assert.assertSame(list, PlayerSubtitleUtils.mergeOverlappingCues(list))
    }

    @Test
    fun isAllowedAddonSubtitle_filtersStandardAndPlusTiers() {
        val enSub = dev.khayin.app.domain.model.Subtitle(id = "1", lang = "en", url = "https://a/en.srt", addonName = "OpenSubtitles", addonLogo = null)
        val zhSub = dev.khayin.app.domain.model.Subtitle(id = "2", lang = "zh", url = "https://a/zh.vtt", addonName = "OpenSubtitles", addonLogo = null)
        val mySub = dev.khayin.app.domain.model.Subtitle(id = "3", lang = "my", url = "https://stream.khayin.net/sub", addonName = "MMSub", addonLogo = null)
        val esSub = dev.khayin.app.domain.model.Subtitle(id = "4", lang = "es", url = "https://a/es.srt", addonName = "OpenSubtitles", addonLogo = null)

        // Plus tier
        assertTrue(PlayerSubtitleUtils.isAllowedAddonSubtitle(enSub, isPlus = true))
        assertTrue(PlayerSubtitleUtils.isAllowedAddonSubtitle(zhSub, isPlus = true))
        assertTrue(PlayerSubtitleUtils.isAllowedAddonSubtitle(mySub, isPlus = true))
        org.junit.Assert.assertFalse(PlayerSubtitleUtils.isAllowedAddonSubtitle(esSub, isPlus = true))

        // Standard tier (no Burmese)
        assertTrue(PlayerSubtitleUtils.isAllowedAddonSubtitle(enSub, isPlus = false))
        assertTrue(PlayerSubtitleUtils.isAllowedAddonSubtitle(zhSub, isPlus = false))
        org.junit.Assert.assertFalse(PlayerSubtitleUtils.isAllowedAddonSubtitle(mySub, isPlus = false))
        org.junit.Assert.assertFalse(PlayerSubtitleUtils.isAllowedAddonSubtitle(esSub, isPlus = false))
    }

    @Test
    fun testPlayerSubtitleCueParser_srt() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            First subtitle

            2
            00:00:05,000 --> 00:00:08,000
            Second subtitle

            3
            00:03:10,000 --> 00:03:15,000
            Three minutes in Burmese: မင်္ဂလာပါ

            4
            01:10:00,000 --> 01:10:05,000
            One hour in
        """.trimIndent()
        val cues = PlayerSubtitleCueParser.parseFromText(srt, "http://example.com/test.srt")
        assertEquals(4, cues.size)
        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals(3000L, cues[0].endTimeMs)
        assertEquals("First subtitle", cues[0].text)

        assertEquals(190000L, cues[2].startTimeMs)
        assertEquals(195000L, cues[2].endTimeMs)
        assertEquals("Three minutes in Burmese: မင်္ဂလာပါ", cues[2].text)

        assertEquals(4200000L, cues[3].startTimeMs)
    }

    @Test
    fun testPlayerSubtitleCueParser_vttWithSettings() {
        val vtt = """
            WEBVTT

            NOTE This is a note

            00:00:01.000 --> 00:00:03.000 align:center position:50%
            First subtitle

            00:03:00.000 --> 00:03:05.000 line:90%
            Second subtitle past 3 min
        """.trimIndent()
        val cues = PlayerSubtitleCueParser.parseFromText(vtt, "http://example.com/test.vtt")
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals(180000L, cues[1].startTimeMs)
    }

    @Test
    fun testPlayerSubtitleCueParser_ass() {
        val ass = """
            [Script Info]
            Title: test
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\an8}Top subtitle
            Dialogue: 0,0:03:15.50,0:03:20.00,Default,,0,0,0,,Burmese \Nမင်္ဂလာပါ
        """.trimIndent()
        val cues = PlayerSubtitleCueParser.parseFromText(ass, "http://example.com/test.ass")
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startTimeMs)
        assertEquals("Top subtitle", cues[0].text)
        assertEquals(195500L, cues[1].startTimeMs)
        assertEquals(200000L, cues[1].endTimeMs)
        assertEquals("Burmese\nမင်္ဂလာပါ", cues[1].text)
    }

    @Test
    fun testPlayerSubtitleCueParser_realKhaYinVtt() {
        val sample = """
WEBVTT - Burmese Auto Subtitles

STYLE
::cue {
  font-family: "Pyidaungsu", "Myanmar Text", "Noto Sans Myanmar", sans-serif;
}

1
00:00:18.708 --> 00:00:22.000
Translated by KhaYin Media

3
00:00:23.291 --> 00:00:24.833
[ရေဒီယိုကြေညာသူ]

29
00:01:54.791 --> 00:01:57.291
[လူတွေ စည်ကားစွာ စကားပြောနေကြသည်]

30
00:01:57.375 --> 00:01:59.375
[စည်တီးသံ စည်းချက်ညီညီ ထွက်ပေါ်နေသည်]

31
00:02:03.625 --> 00:02:05.625
[လူတွေ စကားပြောသံ ဝါးတားတား ကြားနေရသည်]

32
00:02:09.458 --> 00:02:11.458
[တင်းမာပြီး ဒရမ်မာဆန်တဲ့ တေးဂီတသံ ထွက်ပေါ်လာသည်]

38
00:03:19.791 --> 00:03:21.958
ဟေ့၊ ငါ့ဂျာကင်ကို ခိုးတယ်!
        """.trimIndent()
        val cues = PlayerSubtitleCueParser.parseFromText(sample, "https://stream.khayin.net/subtitles/vtt/movie/tt33046197.vtt")
        assertEquals(7, cues.size)
    }

    @Test
    fun testParseSidecarTimedCuesRobust_multiHourVtt() {
        val vtt = """
            WEBVTT

            1
            00:00:10.000 --> 00:00:15.000
            Intro cue

            2
            00:03:30.000 --> 00:03:35.000
            Past three minutes

            3
            01:30:00.000 --> 01:30:05.000
            Hour and a half in

            4
            02:15:00.000 --> 02:15:05.000
            Two hours in
        """.trimIndent()
        val result = parseSidecarTimedCuesRobust(vtt, "https://stream.khayin.net/subtitles/vtt/movie/sample.vtt")
        assertEquals(4, result.cues.size)
        assertEquals(10000000L, result.cues[0].startTimeUs)
        assertEquals(210000000L, result.cues[1].startTimeUs)
        assertEquals(5400000000L, result.cues[2].startTimeUs)
        assertEquals(8100000000L, result.cues[3].startTimeUs)
    }




    @Test
    fun testParseSidecarTimedCuesRobust_retainsAllCuesPastThreeMinutes() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            Hello

            2
            00:03:15,000 --> 00:03:20,000
            At 3 minutes 15 seconds

            3
            00:10:00,000 --> 00:10:05,000
            At 10 minutes
        """.trimIndent()
        val result = parseSidecarTimedCuesRobust(srt, "http://stream.khayin.net/sub.srt")
        assertEquals(3, result.cues.size)
        assertEquals(1000000L, result.cues[0].startTimeUs)
        assertEquals(195000000L, result.cues[1].startTimeUs)
        assertEquals(600000000L, result.cues[2].startTimeUs)
    }
}
