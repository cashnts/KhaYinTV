package dev.khayin.app.ui.screens.player

internal object PlayerSubtitleCueParser {
    private val timestampRegex = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})(?:[.,](\d+))?""")
    private val timingLineRegex = Regex("""(?:(\d+:)?\d{1,2}:\d{2}(?:[.,]\d+)?)\s*-->\s*(?:(\d+:)?\d{1,2}:\d{2}(?:[.,]\d+)?)""")

    fun parseFromText(rawText: String, sourceUrl: String): List<SubtitleSyncCue> {
        val cleanedText = rawText
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        return when {
            looksLikeAss(cleanedText, sourceUrl) -> parseAss(cleanedText)
            looksLikeVtt(cleanedText, sourceUrl) -> parseVtt(cleanedText)
            else -> parseSrt(cleanedText)
        }
    }

    private fun looksLikeAss(text: String, sourceUrl: String): Boolean {
        val normalizedUrl = sourceUrl.substringBefore('?').substringBefore('#').lowercase()
        if (normalizedUrl.endsWith(".ass") || normalizedUrl.endsWith(".ssa")) return true
        return text.contains("[Script Info]", ignoreCase = true) ||
            text.contains("[Events]", ignoreCase = true) ||
            text.contains("Dialogue:", ignoreCase = true)
    }

    private fun looksLikeVtt(text: String, sourceUrl: String): Boolean {
        val normalizedUrl = sourceUrl.substringBefore('?').substringBefore('#').lowercase()
        if (normalizedUrl.endsWith(".vtt") || normalizedUrl.endsWith(".webvtt")) return true
        return text.trimStart().startsWith("WEBVTT", ignoreCase = true)
    }

    private fun parseSrt(text: String): List<SubtitleSyncCue> {
        val lines = text.lines().map { it.trim() }
        val cues = mutableListOf<SubtitleSyncCue>()
        var cursor = 0

        while (cursor < lines.size) {
            val line = lines[cursor]
            if (line.isEmpty()) {
                cursor++
                continue
            }

            var timingLineIndex = -1
            if (line.contains("-->")) {
                timingLineIndex = cursor
            } else if (cursor + 1 < lines.size && lines[cursor + 1].contains("-->")) {
                timingLineIndex = cursor + 1
            }

            if (timingLineIndex == -1) {
                cursor++
                continue
            }

            val timingLine = lines[timingLineIndex]
            val (startTimeMs, endTimeMs) = parseStartEndTimeMs(timingLine) ?: run {
                cursor = timingLineIndex + 1
                return@run null
            } ?: continue

            val textLines = mutableListOf<String>()
            var i = timingLineIndex + 1
            while (i < lines.size) {
                val currentLine = lines[i]
                if (currentLine.isEmpty()) {
                    // Check if next non-empty line starts a new cue
                    var nextNonEmpty = i + 1
                    while (nextNonEmpty < lines.size && lines[nextNonEmpty].isEmpty()) nextNonEmpty++
                    if (nextNonEmpty < lines.size) {
                        val nextCandidate = lines[nextNonEmpty]
                        val isNextTiming = nextCandidate.contains("-->") ||
                            (nextNonEmpty + 1 < lines.size && lines[nextNonEmpty + 1].contains("-->"))
                        if (isNextTiming) break
                    } else {
                        break
                    }
                }
                if (currentLine.contains("-->") || (currentLine.all { it.isDigit() } && i + 1 < lines.size && lines[i + 1].contains("-->"))) {
                    break
                }
                textLines.add(currentLine)
                i++
            }

            val cueText = normalizeCueText(textLines.joinToString("\n"))
            if (cueText.isNotBlank() && endTimeMs > startTimeMs) {
                cues += SubtitleSyncCue(startTimeMs = startTimeMs, endTimeMs = endTimeMs, text = cueText)
            }
            cursor = i
        }

        return cues.sortedBy { it.startTimeMs }
    }

    private fun parseVtt(text: String): List<SubtitleSyncCue> {
        val lines = text.lines().map { it.trim() }
        val cues = mutableListOf<SubtitleSyncCue>()
        var cursor = 0

        while (cursor < lines.size) {
            val line = lines[cursor]
            if (line.isBlank() || line.startsWith("WEBVTT", ignoreCase = true)) {
                cursor++
                continue
            }

            if (isWebVttMetadataBlockHeader(line)) {
                cursor = skipWebVttBlock(lines, cursor + 1)
                continue
            }

            var timingLineIndex = -1
            if (line.contains("-->")) {
                timingLineIndex = cursor
            } else if (cursor + 1 < lines.size && lines[cursor + 1].contains("-->")) {
                timingLineIndex = cursor + 1
            }

            if (timingLineIndex == -1) {
                cursor++
                continue
            }

            val timingLine = lines[timingLineIndex]
            val (startTimeMs, endTimeMs) = parseStartEndTimeMs(timingLine) ?: run {
                cursor = timingLineIndex + 1
                return@run null
            } ?: continue

            val textLines = mutableListOf<String>()
            var i = timingLineIndex + 1
            while (i < lines.size) {
                val currentLine = lines[i]
                if (currentLine.isBlank()) {
                    var nextNonEmpty = i + 1
                    while (nextNonEmpty < lines.size && lines[nextNonEmpty].isEmpty()) nextNonEmpty++
                    if (nextNonEmpty < lines.size) {
                        val nextCandidate = lines[nextNonEmpty]
                        val isNextTiming = nextCandidate.contains("-->") ||
                            (nextNonEmpty + 1 < lines.size && lines[nextNonEmpty + 1].contains("-->"))
                        if (isNextTiming) break
                    } else {
                        break
                    }
                }
                if (currentLine.contains("-->") || (currentLine.all { it.isDigit() } && i + 1 < lines.size && lines[i + 1].contains("-->"))) {
                    break
                }
                textLines.add(currentLine)
                i++
            }

            val cueText = normalizeCueText(textLines.joinToString("\n"))
            if (cueText.isNotBlank() && endTimeMs > startTimeMs) {
                cues += SubtitleSyncCue(startTimeMs = startTimeMs, endTimeMs = endTimeMs, text = cueText)
            }
            cursor = i
        }

        return cues.sortedBy { it.startTimeMs }
    }

    private fun parseAss(text: String): List<SubtitleSyncCue> {
        val cues = mutableListOf<SubtitleSyncCue>()
        val lines = text.lines()
        var inEvents = false
        var formatFields: List<String> = emptyList()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("[Events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (line.startsWith("[") && line.endsWith("]") && !line.equals("[Events]", ignoreCase = true)) {
                inEvents = false
                continue
            }
            if (inEvents && line.startsWith("Format:", ignoreCase = true)) {
                formatFields = line.substringAfter("Format:").split(",").map { it.trim().lowercase() }
                continue
            }
            if (inEvents && line.startsWith("Dialogue:", ignoreCase = true)) {
                val content = line.substringAfter("Dialogue:").trim()
                val parts = if (formatFields.isNotEmpty()) {
                    splitAssDialogue(content, formatFields.size)
                } else {
                    splitAssDialogue(content, 10)
                }

                val startIdx = if (formatFields.isNotEmpty()) formatFields.indexOf("start") else 1
                val endIdx = if (formatFields.isNotEmpty()) formatFields.indexOf("end") else 2
                val textIdx = if (formatFields.isNotEmpty()) formatFields.indexOf("text") else parts.lastIndex

                val startStr = parts.getOrNull(startIdx)?.trim().orEmpty()
                val endStr = parts.getOrNull(endIdx)?.trim().orEmpty()
                val rawCueText = parts.getOrNull(textIdx)?.trim().orEmpty()

                val startMs = parseTimestampMs(startStr) ?: continue
                val endMs = parseTimestampMs(endStr) ?: continue
                if (endMs <= startMs) continue

                // Clean ASS style override tags like {\an8}, \N (newline), etc.
                val cleanedText = rawCueText
                    .replace(Regex("""\{[^}]*\}"""), "")
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ")

                val cueText = normalizeCueText(cleanedText)
                if (cueText.isNotBlank()) {
                    cues += SubtitleSyncCue(startTimeMs = startMs, endTimeMs = endMs, text = cueText)
                }
            }
        }
        return cues.sortedBy { it.startTimeMs }
    }

    private fun splitAssDialogue(line: String, expectedFields: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        for (i in 0 until expectedFields - 1) {
            val comma = line.indexOf(',', start)
            if (comma == -1) break
            result.add(line.substring(start, comma))
            start = comma + 1
        }
        if (start < line.length) {
            result.add(line.substring(start))
        }
        return result
    }

    private fun isWebVttMetadataBlockHeader(line: String): Boolean {
        return line == "STYLE" ||
            line == "REGION" ||
            line == "NOTE" ||
            line.startsWith("NOTE ") ||
            line.startsWith("NOTE\t")
    }

    private fun skipWebVttBlock(lines: List<String>, start: Int): Int {
        var cursor = start
        while (cursor < lines.size && lines[cursor].isNotBlank()) {
            cursor++
        }
        return if (cursor < lines.size) cursor + 1 else cursor
    }

    private fun parseStartEndTimeMs(timingLine: String): Pair<Long, Long>? {
        val parts = timingLine.split("-->")
        if (parts.size != 2) return null
        val startToken = parts[0].trim().split(Regex("""\s+""")).firstOrNull() ?: return null
        val endToken = parts[1].trim().split(Regex("""\s+""")).firstOrNull() ?: return null
        val startTimeMs = parseTimestampMs(startToken) ?: return null
        val endTimeMs = parseTimestampMs(endToken) ?: return null
        return startTimeMs to endTimeMs
    }

    private fun parseTimestampMs(rawTimestamp: String): Long? {
        val clean = rawTimestamp.trim().replace(',', '.')
        val match = timestampRegex.matchEntire(clean) ?: return null
        val hours = match.groupValues[1].removeSuffix(":").toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val millisRaw = match.groupValues[4]
        val millis = when (millisRaw.length) {
            0 -> 0L
            1 -> "${millisRaw}00".toLong()
            2 -> "${millisRaw}0".toLong()
            else -> millisRaw.take(3).toLongOrNull() ?: 0L
        }
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
    }

    private fun normalizeCueText(text: String): String {
        return SubtitleMojibakeSanitizer.sanitize(
            text
                .replace(Regex("""<(?:\d+:)?\d{1,2}:\d{2}(?:[.,]\d+)?>"""), "")
                .replace(Regex("""</?[a-zA-Z0-9._-]+(?: [^>]*)?>"""), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .lines()
                .map { it.replace(Regex("""[ \t]+"""), " ").trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        ).toString()
    }
}

