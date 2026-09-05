package dev.khayin.app.ui.screens.player

import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import dev.khayin.app.ui.util.LANGUAGE_OVERRIDES

object PlayerSubtitleUtils {
    fun normalizeLanguageCode(lang: String): String {
        val code = lang.trim().lowercase()
        if (code.isBlank()) return ""

        val normalizedCode = code.replace('_', '-')
        val tokenized = normalizedCode
            .replace('-', ' ')
            .replace('.', ' ')
            .replace('/', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        fun containsAny(vararg values: String): Boolean = values.any { value ->
            tokenized.contains(value)
        }

        if (containsAny("portuguese", "portugues")) {
            if (containsAny("brazil", "brasil", "brazilian", "brasileiro", "pt br", "ptbr", "pob", "(br)")) {
                return "pt-br"
            }
            if (containsAny("portugal", "european", "europeu", "iberian", "pt pt", "ptpt")) {
                return "pt"
            }
            return "pt"
        }

        if (containsAny("spanish", "espanol", "español", "castellano")) {
            if (containsAny("latin", "latino", "latinoamerica", "latinoamericano", "lat am", "latam", "es 419", "es419", "la", "(419)")) {
                return "es-419"
            }
            return "es"
        }

        // LANGUAGE_OVERRIDES uses pt-BR (mixed case) — normalize to lowercase for consistency
        return LANGUAGE_OVERRIDES[code]?.lowercase() ?: normalizedCode
    }

    fun matchesLanguageCode(language: String?, target: String): Boolean {
        if (language.isNullOrBlank()) return false
        val normalizedLanguage = normalizeLanguageCode(language)
        val normalizedTarget = normalizeLanguageCode(target)
        if (matchesNormalizedLanguage(normalizedLanguage, normalizedTarget)) {
            return true
        }

        val subtags = language.trim().lowercase()
            .replace('_', '-')
            .split('-', '.', '/', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (subtags.size <= 1) {
            return false
        }
        for (subtag in subtags.drop(1)) {
            if (subtag.length != 3) continue
            val normalizedSubtag = normalizeLanguageCode(subtag)
            if (matchesNormalizedLanguage(normalizedSubtag, normalizedTarget)) {
                return true
            }
        }
        return false
    }

    private fun matchesNormalizedLanguage(
        normalizedLanguage: String,
        normalizedTarget: String
    ): Boolean {
        // Exact regional targets: "pt" should not match "pt-br", "es" should not match "es-419"
        if (normalizedTarget == "pt") {
            return normalizedLanguage == "pt"
        }
        if (normalizedTarget == "es") {
            return normalizedLanguage == "es"
        }
        return normalizedLanguage == normalizedTarget ||
            normalizedLanguage.startsWith("$normalizedTarget-") ||
            normalizedLanguage.startsWith("${normalizedTarget}_")
    }

    /**
     * Detects the regional variant of an embedded subtitle track by inspecting
     * its name, language, and trackId fields. Returns a normalized language key
     * that preserves the accent (e.g. "pt-br", "es-419") when detectable,
     * or falls back to the base language code.
     */
    fun detectTrackLanguageVariant(language: String?, name: String?, trackId: String?): String {
        val baseLang = normalizeLanguageCode(language ?: "")
        val haystack = listOfNotNull(name, language, trackId)
            .joinToString(" ")
            .lowercase()

        // Portuguese: detect Brazilian vs European from tags
        if (baseLang == "pt" || baseLang == "por") {
            val hasBrazilian = BRAZILIAN_TAGS.any { haystack.contains(it) }
            val hasEuropean = EUROPEAN_PT_TAGS.any { haystack.contains(it) }
            if (hasBrazilian && !hasEuropean) return "pt-br"
            if (hasEuropean && !hasBrazilian) return "pt"
            return baseLang
        }

        // Spanish: detect Latin American from tags
        if (baseLang == "es" || baseLang == "spa") {
            val hasLatino = LATINO_TAGS.any { haystack.contains(it) }
            val hasCastilian = CASTILIAN_TAGS.any { haystack.contains(it) }
            if (hasLatino && !hasCastilian) return "es-419"
            if (hasCastilian && !hasLatino) return "es"
            return baseLang
        }

        return baseLang
    }

    internal val BRAZILIAN_TAGS = listOf(
        "pt-br", "pt_br", "pob", "brazilian", "brazil", "brasil", "brasileiro", " br", "(br)"
    )
    internal val EUROPEAN_PT_TAGS = listOf(
        "pt-pt", "pt_pt", "iberian", "european", "portugal", "europeu", " eu", "(eu)"
    )
    internal val LATINO_TAGS = listOf(
        "es-419", "es_419", "es-la", "es-lat", "latino", "latinoamerica",
        "latinoamericano", "latam", "lat am", "latin america"
    )
    internal val CASTILIAN_TAGS = listOf(
        "es-es", "es_es", "castilian", "castellano", "spain", "españa", "espana", "iberian"
    )

    fun mimeTypeFromUrl(url: String): String {
        val normalizedPath = url
            .substringBefore('#')
            .substringBefore('?')
            .trimEnd('/')
            .lowercase()

        return when {
            normalizedPath.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            normalizedPath.endsWith(".vtt") || normalizedPath.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            normalizedPath.endsWith(".ass") || normalizedPath.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            normalizedPath.endsWith(".ttml") || normalizedPath.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    /**
     * Sniffs subtitle format from body content, falling back to [mimeTypeFromUrl] when ambiguous.
     * Addon URLs often omit extensions (`/download/12345`), so URL-only mime is frequently wrong.
     */
    fun sniffSubtitleMimeType(rawText: String, sourceUrl: String = ""): String {
        val text = rawText.replace("\uFEFF", "").trimStart()
        if (text.isEmpty()) return mimeTypeFromUrl(sourceUrl)

        if (text.startsWith("WEBVTT", ignoreCase = true)) {
            return MimeTypes.TEXT_VTT
        }

        val head = text.take(4_000)
        if (
            head.startsWith("[Script Info]", ignoreCase = true) ||
            head.contains("[V4+ Styles]", ignoreCase = true) ||
            head.contains("[V4 Styles]", ignoreCase = true) ||
            Regex("""(?im)^\s*Dialogue:""").containsMatchIn(head)
        ) {
            return MimeTypes.TEXT_SSA
        }

        val lowerHead = head.lowercase()
        if (
            (lowerHead.startsWith("<?xml") || lowerHead.contains("<tt ")) &&
            (lowerHead.contains("ttml") || lowerHead.contains(":tt") || lowerHead.contains("<tt "))
        ) {
            return MimeTypes.APPLICATION_TTML
        }

        // SRT: optional index line + HH:MM:SS,mmm --> HH:MM:SS,mmm
        if (
            Regex(
                """(?m)^\d+\s*\r?\n\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}"""
            ).containsMatchIn(text.take(800)) ||
            Regex(
                """(?m)^\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}"""
            ).containsMatchIn(text.take(400))
        ) {
            return MimeTypes.APPLICATION_SUBRIP
        }

        return mimeTypeFromUrl(sourceUrl)
    }

    /**
     * Ordered mime candidates for robust sidecar parsing: sniffed content first, then URL hint,
     * then common text formats.
     */
    fun sidecarMimeCandidates(rawText: String, sourceUrl: String): List<String> {
        val sniffed = sniffSubtitleMimeType(rawText, sourceUrl)
        val fromUrl = mimeTypeFromUrl(sourceUrl)
        return linkedSetOf(
            sniffed,
            fromUrl,
            MimeTypes.APPLICATION_SUBRIP,
            MimeTypes.TEXT_VTT,
            MimeTypes.TEXT_SSA,
            MimeTypes.APPLICATION_TTML
        ).toList()
    }

    fun mergeOverlappingCues(cues: List<Cue>): List<Cue> {
        if (cues.size <= 1) return cues
        val unpositioned = mutableListOf<Cue>()
        val positioned = mutableListOf<Cue>()
        for (cue in cues) {
            if (cue.line == Cue.DIMEN_UNSET && cue.position == Cue.DIMEN_UNSET) {
                unpositioned.add(cue)
            } else {
                positioned.add(cue)
            }
        }
        if (unpositioned.size <= 1) return cues

        val mergedBuilder = StringBuilder()
        unpositioned.forEachIndexed { index, cue ->
            val text = cue.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                if (index > 0 && mergedBuilder.isNotEmpty()) {
                    mergedBuilder.append("\n")
                }
                mergedBuilder.append(text)
            }
        }
        val firstUnpositioned = unpositioned.first()
        val mergedCue = firstUnpositioned.buildUpon()
            .setText(mergedBuilder.toString())
            .build()
        return listOf(mergedCue) + positioned
    }

    /**
     * Checks if an internal subtitle track is allowed based on language and user tier (Plus vs Standard).
     * Allowed: English, Chinese, and Burmese (Plus only).
     */
    fun isAllowedSubtitleTrack(track: TrackInfo, isPlus: Boolean): Boolean {
        val code = track.language?.trim().orEmpty()
        val lbl = track.name.trim()
        val id = track.trackId?.trim().orEmpty()
        val combined = "$code $lbl $id".trim()

        val normalizedCode = normalizeLanguageCode(code).lowercase().substringBefore('-')
        val normalizedLabel = normalizeLanguageCode(lbl).lowercase().substringBefore('-')

        val isEnglish = normalizedCode == "en" || normalizedCode == "eng" ||
                        normalizedLabel == "en" || normalizedLabel == "eng" ||
                        combined.contains("english", ignoreCase = true) ||
                        code.equals("en", ignoreCase = true) ||
                        code.equals("eng", ignoreCase = true)

        val isChinese = normalizedCode == "zh" || normalizedCode == "zho" || normalizedCode == "chi" || normalizedCode == "cmn" || normalizedCode == "yue" ||
                        normalizedLabel == "zh" || normalizedLabel == "zho" || normalizedLabel == "chi" ||
                        combined.contains("chinese", ignoreCase = true) ||
                        combined.contains("mandarin", ignoreCase = true) ||
                        combined.contains("cantonese", ignoreCase = true) ||
                        combined.contains("中文", ignoreCase = true) ||
                        code.equals("zh", ignoreCase = true) ||
                        code.equals("chi", ignoreCase = true) ||
                        code.equals("zho", ignoreCase = true)

        val isBurmese = normalizedCode == "my" || normalizedCode == "mya" || normalizedCode == "bur" ||
                        normalizedLabel == "my" || normalizedLabel == "mya" || normalizedLabel == "bur" ||
                        combined.contains("burmese", ignoreCase = true) ||
                        combined.contains("myanmar", ignoreCase = true) ||
                        combined.contains("mmsub", ignoreCase = true) ||
                        combined.contains("မြန်မာ", ignoreCase = true) ||
                        code.equals("my", ignoreCase = true) ||
                        code.equals("bur", ignoreCase = true) ||
                        code.equals("mya", ignoreCase = true)

        return when {
            isPlus -> isEnglish || isChinese || isBurmese
            else -> isEnglish || isChinese
        }
    }

    /**
     * Checks if an addon subtitle is allowed based on language and user tier (Plus vs Standard).
     * Allowed: English, Chinese, and Burmese (Plus only).
     */
    fun isAllowedAddonSubtitle(subtitle: dev.khayin.app.domain.model.Subtitle, isPlus: Boolean): Boolean {
        val code = subtitle.lang.trim()
        val addon = subtitle.addonName.trim()
        val id = subtitle.id.trim()
        val combined = "$code $addon $id ${subtitle.url}".trim()

        val normalizedCode = normalizeLanguageCode(code).lowercase().substringBefore('-')

        val isEnglish = normalizedCode == "en" || normalizedCode == "eng" ||
                        combined.contains("english", ignoreCase = true) ||
                        code.equals("en", ignoreCase = true) ||
                        code.equals("eng", ignoreCase = true)

        val isChinese = normalizedCode == "zh" || normalizedCode == "zho" || normalizedCode == "chi" || normalizedCode == "cmn" || normalizedCode == "yue" ||
                        combined.contains("chinese", ignoreCase = true) ||
                        combined.contains("mandarin", ignoreCase = true) ||
                        combined.contains("cantonese", ignoreCase = true) ||
                        combined.contains("中文", ignoreCase = true) ||
                        code.equals("zh", ignoreCase = true) ||
                        code.equals("chi", ignoreCase = true) ||
                        code.equals("zho", ignoreCase = true)

        val isBurmese = normalizedCode == "my" || normalizedCode == "mya" || normalizedCode == "bur" ||
                        combined.contains("burmese", ignoreCase = true) ||
                        combined.contains("myanmar", ignoreCase = true) ||
                        combined.contains("mmsub", ignoreCase = true) ||
                        combined.contains("မြန်မာ", ignoreCase = true) ||
                        subtitle.url.contains("stream.khayin.net", ignoreCase = true) ||
                        code.equals("my", ignoreCase = true) ||
                        code.equals("bur", ignoreCase = true) ||
                        code.equals("mya", ignoreCase = true)

        return when {
            isPlus -> isEnglish || isChinese || isBurmese
            else -> isEnglish || isChinese
        }
    }

    /**
     * Checks if a language code / label is allowed for subtitle options.
     */
    fun isAllowedSubtitleLanguageCode(code: String?, label: String? = null, isPlus: Boolean): Boolean {
        val raw = code?.trim().orEmpty()
        val lbl = label?.trim().orEmpty()
        val combined = "$raw $lbl".trim()

        val normalized = normalizeLanguageCode(raw).lowercase().substringBefore('-')

        val isEnglish = normalized == "en" || normalized == "eng" ||
                        combined.contains("english", ignoreCase = true) ||
                        raw.equals("en", ignoreCase = true) ||
                        raw.equals("eng", ignoreCase = true)

        val isChinese = normalized == "zh" || normalized == "zho" || normalized == "chi" || normalized == "cmn" || normalized == "yue" ||
                        combined.contains("chinese", ignoreCase = true) ||
                        combined.contains("mandarin", ignoreCase = true) ||
                        combined.contains("cantonese", ignoreCase = true) ||
                        combined.contains("中文", ignoreCase = true) ||
                        raw.equals("zh", ignoreCase = true) ||
                        raw.equals("chi", ignoreCase = true) ||
                        raw.equals("zho", ignoreCase = true)

        val isBurmese = normalized == "my" || normalized == "mya" || normalized == "bur" ||
                        combined.contains("burmese", ignoreCase = true) ||
                        combined.contains("myanmar", ignoreCase = true) ||
                        combined.contains("mmsub", ignoreCase = true) ||
                        combined.contains("မြန်မာ", ignoreCase = true) ||
                        raw.equals("my", ignoreCase = true) ||
                        raw.equals("bur", ignoreCase = true) ||
                        raw.equals("mya", ignoreCase = true)

        return when {
            isPlus -> isEnglish || isChinese || isBurmese
            else -> isEnglish || isChinese
        }
    }

    /**
     * Strips query parameters from static subtitle URLs (such as stream.khayin.net ?extra=...)
     * where query strings cause the upstream CDN to return a truncated 30-cue sample instead of the full file.
     */
    fun cleanSubtitleUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.contains("stream.khayin.net", ignoreCase = true) && trimmed.contains("?extra=", ignoreCase = true)) {
            return trimmed.substringBefore("?")
        }
        return trimmed
    }
}


