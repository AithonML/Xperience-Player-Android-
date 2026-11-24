package com.xperience.player.widget

import com.xperience.player.domain.model.RadioStationItem
import com.xperience.player.domain.model.RecommendedPlaylistItem
import com.xperience.player.domain.model.TrackItem
import com.xperience.player.domain.model.UserPlaylist
import java.util.Locale

/**
 * Pure recommendation curation, ranking, deduplication, and fallback engine for widgets.
 *
 * Interleaves discovery playlists ("Made For You"), auto-generated smart mixes,
 * saved user playlists, radio stations, and virtual Liked / Recently Played mixes so
 * widgets always show diverse, playable shortcuts both online and offline.
 */
object WidgetRecommendationCurator {

    const val LIKED_SONGS_VIRTUAL_ID = "widget_virtual_liked_songs"
    const val RECENTLY_PLAYED_VIRTUAL_ID = "widget_virtual_recently_played"
    const val DEFAULT_MAX_ITEMS = 12

    fun curate(
        userPlaylists: List<UserPlaylist>,
        smartPlaylists: List<UserPlaylist>,
        madeForYou: List<RecommendedPlaylistItem>,
        radioStations: List<RadioStationItem>,
        likedCount: Int,
        likedArtworkUri: String? = null,
        recentlyPlayedTracks: List<TrackItem> = emptyList(),
        likedTitleFallback: String = "Liked Songs",
        recentTitleFallback: String = "Recently Played",
        maxItems: Int = DEFAULT_MAX_ITEMS
    ): List<WidgetPlaylistItem> {
        val limit = maxItems.coerceAtLeast(1)

        val madeForYouItems = madeForYou
            .asSequence()
            .filter { it.title.isNotBlank() && (it.playlistId.isNotBlank() || it.id.isNotBlank()) }
            .map { item ->
                val resolvedId = item.playlistId.ifBlank { item.id }
                val subtitle = item.trackCountText?.takeIf { it.isNotBlank() }
                    ?: item.author?.takeIf { it.isNotBlank() }
                    ?: item.subtitle.takeIf { it.isNotBlank() }
                    ?: ""
                WidgetPlaylistItem(
                    id = resolvedId,
                    title = item.title.trim(),
                    subtitle = subtitle.trim(),
                    trackCount = extractTrackCount(item.trackCountText),
                    artworkUri = item.artworkUri?.trim()?.ifBlank { null },
                    sourceType = WidgetPlaylistSourceType.MADE_FOR_YOU,
                    playlistId = item.playlistId.ifBlank { null },
                    browseId = item.browseId?.trim()?.ifBlank { null }
                )
            }
            .toList()

        val smartItems = smartPlaylists
            .asSequence()
            .filter { it.title.isNotBlank() && it.tracks.isNotEmpty() }
            .map { pl ->
                WidgetPlaylistItem(
                    id = pl.id,
                    title = pl.title.trim(),
                    subtitle = pl.suggestionBadge.trim().ifBlank { pl.trackCountSubtitle },
                    trackCount = pl.tracks.size,
                    artworkUri = resolvePlaylistArtwork(pl),
                    sourceType = WidgetPlaylistSourceType.SMART_PLAYLIST
                )
            }
            .toList()

        // Prefer non-empty user playlists sorted by richest track count; keep empty playlists only as last-resort fallback.
        val populatedUserItems = userPlaylists
            .asSequence()
            .filter { it.title.isNotBlank() && it.tracks.isNotEmpty() }
            .sortedByDescending { it.tracks.size }
            .map { pl ->
                WidgetPlaylistItem(
                    id = pl.id,
                    title = pl.title.trim(),
                    subtitle = pl.trackCountSubtitle,
                    trackCount = pl.tracks.size,
                    artworkUri = resolvePlaylistArtwork(pl),
                    sourceType = WidgetPlaylistSourceType.USER_PLAYLIST
                )
            }
            .toList()

        val radioItems = radioStations
            .asSequence()
            .filter { it.title.isNotBlank() && (it.playlistId.isNotBlank() || it.id.isNotBlank()) }
            .map { station ->
                WidgetPlaylistItem(
                    id = station.playlistId.ifBlank { station.id },
                    title = station.title.trim(),
                    subtitle = station.subtitle.trim(),
                    trackCount = 0,
                    artworkUri = station.artworkUri?.trim()?.ifBlank { null },
                    sourceType = WidgetPlaylistSourceType.RADIO_STATION,
                    playlistId = station.playlistId.ifBlank { null },
                    seedVideoId = station.seedVideoId?.trim()?.ifBlank { null },
                    params = station.params?.trim()?.ifBlank { null }
                )
            }
            .toList()

        val likedItem: WidgetPlaylistItem? = if (likedCount > 0) {
            WidgetPlaylistItem(
                id = LIKED_SONGS_VIRTUAL_ID,
                title = likedTitleFallback,
                subtitle = "",
                trackCount = likedCount,
                artworkUri = likedArtworkUri?.trim()?.ifBlank { null },
                sourceType = WidgetPlaylistSourceType.LIKED_SONGS
            )
        } else {
            null
        }

        val recentItem: WidgetPlaylistItem? = if (recentlyPlayedTracks.isNotEmpty()) {
            WidgetPlaylistItem(
                id = RECENTLY_PLAYED_VIRTUAL_ID,
                title = recentTitleFallback,
                subtitle = "",
                trackCount = recentlyPlayedTracks.size,
                artworkUri = recentlyPlayedTracks.firstNotNullOfOrNull {
                    it.artworkUri?.trim()?.ifBlank { null }
                },
                sourceType = WidgetPlaylistSourceType.RECENTLY_PLAYED
            )
        } else {
            null
        }

        val result = mutableListOf<WidgetPlaylistItem>()
        val seenKeys = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()

        fun tryAdd(candidate: WidgetPlaylistItem?): Boolean {
            if (candidate == null || result.size >= limit) return false
            val idKey = "${candidate.sourceType.wireValue}::${candidate.id.trim().lowercase(Locale.ROOT)}"
            val titleKey = candidate.title.trim().lowercase(Locale.ROOT)
            if (titleKey.isBlank()) return false
            if (!seenKeys.add(idKey)) return false
            if (!seenTitles.add(titleKey)) return false
            result.add(candidate)
            return true
        }

        // Round-robin interleaving across primary buckets for a varied, personalized grid.
        val buckets = mutableListOf<ArrayDeque<WidgetPlaylistItem>>()
        if (madeForYouItems.isNotEmpty()) buckets.add(ArrayDeque(madeForYouItems))
        if (smartItems.isNotEmpty()) buckets.add(ArrayDeque(smartItems))
        if (populatedUserItems.isNotEmpty()) buckets.add(ArrayDeque(populatedUserItems))
        if (radioItems.isNotEmpty()) buckets.add(ArrayDeque(radioItems))

        var round = 0
        while (result.size < limit && buckets.any { it.isNotEmpty() }) {
            for (bucket in buckets) {
                if (result.size >= limit) break
                while (bucket.isNotEmpty()) {
                    val next = bucket.removeFirst()
                    if (tryAdd(next)) break
                }
            }
            // Inject Liked Songs prominently after the first round so offline favorites are always 1 tap away.
            if (round == 0) {
                tryAdd(likedItem)
            }
            round++
        }

        // Fallbacks when online discovery feeds are unavailable or empty.
        tryAdd(likedItem)
        tryAdd(recentItem)

        if (result.size < limit) {
            userPlaylists
                .asSequence()
                .filter { it.title.isNotBlank() && it.tracks.isEmpty() }
                .forEach { emptyPl ->
                    tryAdd(
                        WidgetPlaylistItem(
                            id = emptyPl.id,
                            title = emptyPl.title.trim(),
                            subtitle = emptyPl.trackCountSubtitle,
                            trackCount = 0,
                            artworkUri = resolvePlaylistArtwork(emptyPl),
                            sourceType = WidgetPlaylistSourceType.USER_PLAYLIST
                        )
                    )
                }
        }

        return result
    }

    /**
     * Selects up to [count] items starting at cyclic [offset], never repeating the same item twice.
     */
    fun selectWindow(
        items: List<WidgetPlaylistItem>,
        offset: Int,
        count: Int
    ): List<WidgetPlaylistItem> {
        if (items.isEmpty() || count <= 0) return emptyList()
        val takeCount = minOf(items.size, count)
        val safeOffset = ((offset % items.size) + items.size) % items.size
        return List(takeCount) { idx ->
            items[(safeOffset + idx) % items.size]
        }
    }

    /**
     * Stable content signature used to avoid redundant disk writes and widget redraws.
     */
    fun buildSignature(items: List<WidgetPlaylistItem>): String {
        return items.joinToString(separator = "\n") { item ->
            "${item.sourceType.wireValue}|${item.id}|${item.title}|${item.subtitle}|${item.trackCount}|${item.artworkUri.orEmpty()}"
        }
    }

    private fun resolvePlaylistArtwork(playlist: UserPlaylist): String? {
        return playlist.customCoverImagePath.trim().ifBlank { null }
            ?: playlist.collageCover1?.trim()?.ifBlank { null }
            ?: playlist.tracks.firstNotNullOfOrNull { it.artworkUri?.trim()?.ifBlank { null } }
    }

    private fun extractTrackCount(trackCountText: String?): Int {
        if (trackCountText.isNullOrBlank()) return 0
        val digits = Regex("""\d+""").find(trackCountText)?.value ?: return 0
        return digits.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}
