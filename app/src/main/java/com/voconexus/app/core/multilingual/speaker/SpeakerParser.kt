package com.voconexus.app.core.multilingual.speaker

data class SpeakerSegment(
    val speakerId: String,
    val spokenText: String,
    val rawText: String
)

class SpeakerParser {

    companion object {
        private val DIALOGUE_PATTERN = "^([A-Z][a-zA-Z0-9_]{1,16}):\\s*(.+)".toRegex()
        private val EXCLUDED_LABELS = setOf("NOTE", "WARNING", "TIP", "IMPORTANT", "HTTP", "HTTPS", "TIME", "CHAPTER", "PART", "PAGE")
    }

    fun parseDialogueLine(line: String): SpeakerSegment {
        val trimmed = line.trim()
        val match = DIALOGUE_PATTERN.find(trimmed)

        if (match != null) {
            val candidateSpeaker = match.groupValues[1].trim()
            val spokenContent = match.groupValues[2].trim()

            if (!EXCLUDED_LABELS.contains(candidateSpeaker.uppercase())) {
                return SpeakerSegment(
                    speakerId = candidateSpeaker,
                    spokenText = spokenContent,
                    rawText = trimmed
                )
            }
        }

        return SpeakerSegment(
            speakerId = "Narrator",
            spokenText = trimmed,
            rawText = trimmed
        )
    }

    fun parseScriptLines(script: String): List<SpeakerSegment> {
        if (script.isBlank()) return emptyList()
        val lines = script.split("\n+").map { it.trim() }.filter { it.isNotBlank() }
        return lines.map { parseDialogueLine(it) }
    }
}
