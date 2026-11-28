package com.xperience.player.domain.model

data class ArtistItem(
    val id: String,
    val name: String,
    val avatarResId: Int? = null,
    val avatarUri: String? = null,
    val genre: String = "Pop",
    val monthlyListeners: String = "2,88 M",
    val subscribers: String = "3,14 M",
    val browseId: String? = null
) {
    /**
     * Extracts a canonical YouTube Music channel browseId (`UC...` or library artist detail ID)
     * from [browseId] or encoded [id] prefixes such as `ytm_sim_UC...` / `ytm_artist_UC...`.
     */
    fun extractChannelBrowseId(): String? {
        val explicit = browseId?.trim()?.takeIf { isValidChannelBrowseId(it) }
        if (explicit != null) return explicit

        val raw = id.trim()
        val stripped = when {
            raw.startsWith("ytm_sim_") -> raw.removePrefix("ytm_sim_")
            raw.startsWith("ytm_artist_") -> raw.removePrefix("ytm_artist_")
            else -> raw
        }.trim()
        val normalized = if (stripped.startsWith("MPLAUC")) stripped.removePrefix("MPLA") else stripped
        return normalized.takeIf { isValidChannelBrowseId(it) }
    }

    companion object {
        internal fun isValidChannelBrowseId(value: String): Boolean {
            return value.startsWith("UC") ||
                value.startsWith("FEmusic_library_privately_owned_artist_detail")
        }
    }
}
