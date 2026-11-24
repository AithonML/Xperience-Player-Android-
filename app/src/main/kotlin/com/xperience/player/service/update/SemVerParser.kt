package com.xperience.player.service.update

import com.xperience.player.domain.model.update.SemVer

/**
 * SemVer 2.0.0 parser & comparator.
 *
 * Strips a single leading `v` or `V` prefix (e.g. `v1.0.1` -> `1.0.1`) and supports
 * full pre-release & build metadata parsing, plus 2-segment normalization for app
 * version names such as `0.1-Beta` -> `0.1.0-Beta`.
 */
object SemVerParser {

    private val IDENTIFIER_REGEX = Regex("^[0-9A-Za-z-]+$")
    private val NUMERIC_SEGMENT_REGEX = Regex("^(0|[1-9][0-9]*)$")

    fun parse(raw: String?): SemVer? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) {
            trimmed.substring(1)
        } else {
            trimmed
        }
        if (withoutPrefix.isEmpty()) return null

        // Split optional build metadata (+...)
        val plusIndex = withoutPrefix.indexOf('+')
        val buildMetadata: String?
        val withoutBuild: String
        if (plusIndex >= 0) {
            buildMetadata = withoutPrefix.substring(plusIndex + 1)
            withoutBuild = withoutPrefix.substring(0, plusIndex)
            if (buildMetadata.isEmpty()) return null
            val buildParts = buildMetadata.split('.')
            if (buildParts.any { it.isEmpty() || !IDENTIFIER_REGEX.matches(it) }) return null
        } else {
            buildMetadata = null
            withoutBuild = withoutPrefix
        }

        // Split optional pre-release (-...)
        val dashIndex = withoutBuild.indexOf('-')
        val preReleaseParts: List<String>
        val corePart: String
        if (dashIndex >= 0) {
            val preReleaseRaw = withoutBuild.substring(dashIndex + 1)
            corePart = withoutBuild.substring(0, dashIndex)
            if (preReleaseRaw.isEmpty()) return null
            preReleaseParts = preReleaseRaw.split('.')
            if (preReleaseParts.any { it.isEmpty() || !IDENTIFIER_REGEX.matches(it) }) return null
            // Numeric pre-release identifiers must not include leading zeroes per SemVer 2.0.0 §9
            for (part in preReleaseParts) {
                if (part.all { it in '0'..'9' } && part.length > 1 && part.startsWith('0')) {
                    return null
                }
            }
        } else {
            preReleaseParts = emptyList()
            corePart = withoutBuild
        }

        val coreSegments = corePart.split('.')
        if (coreSegments.size != 2 && coreSegments.size != 3) return null
        if (coreSegments.any { !NUMERIC_SEGMENT_REGEX.matches(it) }) return null

        val major = coreSegments[0].toIntOrNull() ?: return null
        val minor = coreSegments[1].toIntOrNull() ?: return null
        val patch = if (coreSegments.size == 3) {
            coreSegments[2].toIntOrNull() ?: return null
        } else {
            0
        }

        return SemVer(
            major = major,
            minor = minor,
            patch = patch,
            preRelease = preReleaseParts,
            buildMetadata = buildMetadata
        )
    }

    /**
     * Returns true iff both versions parse cleanly and [candidateTag] is strictly newer than [currentVersion].
     */
    fun isNewerVersion(candidateTag: String?, currentVersion: String?): Boolean {
        val candidate = parse(candidateTag) ?: return false
        val current = parse(currentVersion) ?: return false
        return candidate.isNewerThan(current)
    }

    /**
     * Formats a version string consistently with a leading `v` for UI display (e.g. `0.1-Beta` -> `v0.1-Beta`).
     */
    fun formatDisplayTag(rawVersion: String?): String {
        val cleaned = rawVersion?.trim().orEmpty()
        if (cleaned.isEmpty()) return "v0.0.0"
        return if (cleaned.startsWith("v", ignoreCase = true)) {
            "v${cleaned.substring(1)}"
        } else {
            "v$cleaned"
        }
    }
}
