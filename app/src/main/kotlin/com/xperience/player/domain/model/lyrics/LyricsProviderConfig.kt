package com.xperience.player.domain.model.lyrics

/**
 * Configurable priority and state entry for an online lyrics provider.
 */
data class LyricsProviderConfig(
    val id: LyricsProviderId,
    val displayName: String,
    val capabilityBadge: String,
    var priorityOrder: Int = 1,
    var isEnabled: Boolean = true
) {
    val priorityLabel: String
        get() = "#$priorityOrder"

    val statusLabel: String
        get() = if (isEnabled) "Enabled" else "Off"
}
