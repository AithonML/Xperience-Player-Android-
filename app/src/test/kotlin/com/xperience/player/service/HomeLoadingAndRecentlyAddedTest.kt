package com.xperience.player.service

import android.content.SharedPreferences
import com.xperience.player.domain.model.HomeDiscoveryFeed
import com.xperience.player.domain.model.TrackItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLoadingAndRecentlyAddedTest {

    private fun sampleTrack(
        id: String,
        title: String = "Track $id",
        dateAddedMs: Long = 0L
    ): TrackItem = TrackItem(
        id = id,
        title = title,
        artist = "Artist $id",
        album = "Album $id",
        durationMs = 180_000L,
        dateAddedMs = dateAddedMs
    )

    @Test
    fun trackItemAndHomeFeedDefaultsPreserveLoadingAndTimestampCompatibility() {
        val track = sampleTrack("t1")
        assertEquals(0L, track.dateAddedMs)

        val defaultFeed = HomeDiscoveryFeed(
            personalizedSubtitle = "Para ti",
            recentlyPlayedTracks = emptyList(),
            relatedArtists = emptyList()
        )
        assertTrue(defaultFeed.isFeedLoading)

        val settledFeed = defaultFeed.copy(isFeedLoading = false)
        assertFalse(settledFeed.isFeedLoading)
    }

    @Test
    fun resolveMediaStoreDateAddedMsConvertsSecondsAndAppliesFallbacks() {
        // MediaStore DATE_ADDED is in seconds -> converts to epoch milliseconds
        assertEquals(
            1_700_000_000_000L,
            resolveMediaStoreDateAddedMs(
                dateAddedSeconds = 1_700_000_000L,
                dateModifiedSeconds = 1_650_000_000L,
                fileLastModifiedMs = 1_600_000_000_000L
            )
        )

        // Already-millisecond timestamp passes through unscaled
        assertEquals(
            1_700_000_000_123L,
            resolveMediaStoreDateAddedMs(
                dateAddedSeconds = 1_700_000_000_123L,
                dateModifiedSeconds = 0L,
                fileLastModifiedMs = 0L
            )
        )

        // Falls back to DATE_MODIFIED when DATE_ADDED is 0
        assertEquals(
            1_650_000_000_000L,
            resolveMediaStoreDateAddedMs(
                dateAddedSeconds = 0L,
                dateModifiedSeconds = 1_650_000_000L,
                fileLastModifiedMs = 1_600_000_000_000L
            )
        )

        // Falls back to File.lastModified() when MediaStore timestamps are missing
        assertEquals(
            1_600_000_000_000L,
            resolveMediaStoreDateAddedMs(
                dateAddedSeconds = 0L,
                dateModifiedSeconds = -1L,
                fileLastModifiedMs = 1_600_000_000_000L
            )
        )

        // Returns 0 when all sources are non-positive
        assertEquals(
            0L,
            resolveMediaStoreDateAddedMs(
                dateAddedSeconds = 0L,
                dateModifiedSeconds = 0L,
                fileLastModifiedMs = 0L
            )
        )
    }

    @Test
    fun trackAddedAtStorePreservesLocalTimestampsAndPersistsOnlineFirstSeenTimestamps() {
        assertEquals("xperience_added_timestamps_prefs", TrackAddedAtStore.PREFS_NAME)

        val prefs = FakeSharedPreferences()
        var nowMs = 1_750_000_000_000L
        val store = TrackAddedAtStore(prefs = prefs, clockMs = { nowMs })

        val localTrack = sampleTrack("local_1", dateAddedMs = 1_680_000_000_000L)
        val onlineFirstSeen = sampleTrack("yt_song_a", dateAddedMs = 0L)

        val stampedLocal = store.stampTrack(localTrack)
        val stampedOnline = store.stampTrack(onlineFirstSeen)

        // Real local timestamp is untouched and not written into online prefs
        assertEquals(1_680_000_000_000L, stampedLocal.dateAddedMs)
        assertNull(store.getTimestamp("local_1"))

        // Online track gets first-seen timestamp and persists across restarts
        assertEquals(1_750_000_000_000L, stampedOnline.dateAddedMs)
        assertEquals(1_750_000_000_000L, prefs.getLong("yt_song_a", -1L))

        // Simulate app restart much later with a fresh incoming copy (dateAddedMs = 0L)
        nowMs = 1_800_000_000_000L
        val restartedStore = TrackAddedAtStore(prefs = prefs, clockMs = { nowMs })
        val reseenOnline = restartedStore.stampTrack(sampleTrack("yt_song_a", dateAddedMs = 0L))
        assertEquals(1_750_000_000_000L, reseenOnline.dateAddedMs)

        // Newly seen online track gets the later timestamp
        val newOnline = restartedStore.stampTrack(sampleTrack("yt_song_b", dateAddedMs = 0L))
        assertEquals(1_800_000_000_000L, newOnline.dateAddedMs)
    }

    @Test
    fun trackAddedAtStoreStampTracksCommitsBatchInSingleApplyAndHandlesBlankIds() {
        val prefs = FakeSharedPreferences()
        val nowMs = 1_760_000_000_000L
        val store = TrackAddedAtStore(prefs = prefs, clockMs = { nowMs })

        val batch = listOf(
            sampleTrack("local_existing", dateAddedMs = 1_700_000_000_000L),
            sampleTrack("yt_1", dateAddedMs = 0L),
            sampleTrack("yt_2", dateAddedMs = 0L),
            sampleTrack("", dateAddedMs = 0L)
        )

        val stamped = store.stampTracks(batch)

        assertEquals(1_700_000_000_000L, stamped[0].dateAddedMs)
        assertEquals(nowMs - 1L, stamped[1].dateAddedMs)
        assertEquals(nowMs - 2L, stamped[2].dateAddedMs)
        assertEquals(nowMs - 3L, stamped[3].dateAddedMs)
        assertEquals(1, prefs.applyCount)
        assertNull(store.getTimestamp(""))

        val sorted = sortRecentlyAddedLibraryTracks(stamped.shuffled())
        assertEquals(
            listOf("yt_1", "yt_2", "", "local_existing"),
            sorted.map { it.id }
        )
    }

    @Test
    fun sortRecentlyAddedLibraryTracksDeduplicatesAndSortsMixedLocalOnlineDescending() {
        val mixed = listOf(
            sampleTrack("old_local", title = "Alpha", dateAddedMs = 1_000L),
            sampleTrack("yt_dup", title = "Mid", dateAddedMs = 0L),
            sampleTrack("newest_online", title = "Zeta", dateAddedMs = 9_000L),
            sampleTrack("yt_dup", title = "Mid", dateAddedMs = 5_000L),
            sampleTrack("newer_local", title = "Beta", dateAddedMs = 7_500L),
            sampleTrack("undated_b", title = "Bravo", dateAddedMs = 0L),
            sampleTrack("undated_a", title = "Able", dateAddedMs = 0L)
        )

        val sorted = sortRecentlyAddedLibraryTracks(mixed)

        assertEquals(
            listOf("newest_online", "newer_local", "yt_dup", "old_local", "undated_a", "undated_b"),
            sorted.map { it.id }
        )
        assertEquals(5_000L, sorted.first { it.id == "yt_dup" }.dateAddedMs)
    }

    @Test
    fun ensureUniqueNonBlankIdsAssignsDeterministicFallbacksAndRemovesDuplicates() {
        val items = listOf(
            sampleTrack("", title = "First Blank"),
            sampleTrack("", title = "Second Blank"),
            sampleTrack("   ", title = "first blank"),
            sampleTrack("yt_1", title = "Existing A"),
            sampleTrack("yt_1", title = "Existing Duplicate")
        )

        val normalized = items.ensureUniqueNonBlankIds(
            prefix = "trk",
            getId = { it.id },
            getLabel = { it.title },
            withId = { item, newId -> item.copy(id = newId) }
        )

        assertEquals(3, normalized.size)
        assertTrue(normalized.all { it.id.isNotBlank() })
        assertEquals(normalized.size, normalized.map { it.id }.toSet().size)
        assertEquals(listOf("First Blank", "Second Blank", "Existing A"), normalized.map { it.title })
    }

    private class FakeSharedPreferences : SharedPreferences {
        private val data = LinkedHashMap<String, Any?>()
        var applyCount: Int = 0
            private set

        override fun getAll(): MutableMap<String, *> = LinkedHashMap(data)

        override fun getString(key: String?, defValue: String?): String? =
            (data[key] as? String) ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int =
            (data[key] as? Int) ?: defValue

        override fun getLong(key: String?, defValue: Long): Long =
            (data[key] as? Long) ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float =
            (data[key] as? Float) ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            (data[key] as? Boolean) ?: defValue

        override fun contains(key: String?): Boolean = data.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = LinkedHashMap<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
                pending.clear()
            }

            override fun commit(): Boolean {
                flush()
                return true
            }

            override fun apply() {
                applyCount += 1
                flush()
            }

            private fun flush() {
                if (clearAll) data.clear()
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit
    }
}
