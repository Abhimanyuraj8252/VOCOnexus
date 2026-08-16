package com.voconexus.app.core.multilingual.routing

import com.voconexus.app.core.multilingual.language.LanguageSegment
import com.voconexus.app.core.multilingual.language.VoiceProfile
import com.voconexus.app.core.multilingual.language.VoiceRoutingReport

data class VoiceAssignmentResult(
    val voiceProfile: VoiceProfile,
    val isFallbackUsed: Boolean = false,
    val fallbackReason: String? = null
)

class VoiceRouter {

    fun routeSegment(
        segment: LanguageSegment,
        availableVoices: List<VoiceProfile>,
        projectDefaultVoice: VoiceProfile,
        speakerMappings: Map<String, VoiceProfile> = emptyMap(),
        languageMappings: Map<String, VoiceProfile> = emptyMap(),
        manualSegmentOverrides: Map<String, VoiceProfile> = emptyMap()
    ): VoiceAssignmentResult {
        // 1. Explicit Manual Segment Override
        manualSegmentOverrides[segment.id]?.let {
            return VoiceAssignmentResult(it, false, null)
        }

        // 2. Explicit Speaker Mapping
        segment.speakerId?.let { speaker ->
            speakerMappings[speaker]?.let {
                return VoiceAssignmentResult(it, false, null)
            }
        }

        // 3. Explicit Language Mapping
        languageMappings[segment.languageCode]?.let {
            return VoiceAssignmentResult(it, false, null)
        }

        // 4. Compatible Automatic Voice Lookup
        val exactMatch = availableVoices.firstOrNull {
            it.isInstalled && (it.languageCode == segment.languageCode || it.languageCode.startsWith(segment.languageCode))
        }
        if (exactMatch != null) {
            return VoiceAssignmentResult(exactMatch, false, null)
        }

        // 5. Fallback Policy: Project Default Voice
        return VoiceAssignmentResult(
            voiceProfile = projectDefaultVoice,
            isFallbackUsed = true,
            fallbackReason = "No exact voice installed for language '${segment.languageCode}'. Used project default voice '${projectDefaultVoice.displayName}'."
        )
    }

    fun generateRoutingReport(results: List<VoiceAssignmentResult>): VoiceRoutingReport {
        val total = results.size
        val fallbacks = results.count { it.isFallbackUsed }
        val preferred = total - fallbacks
        return VoiceRoutingReport(
            totalSegments = total,
            preferredVoiceCount = preferred,
            fallbackVoiceCount = fallbacks,
            unresolvedCount = 0
        )
    }
}
