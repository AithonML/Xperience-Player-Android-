package com.xperience.player.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xperience.player.MainActivity

/**
 * Explicit, package-scoped widget action constants and `FLAG_IMMUTABLE` PendingIntent factories
 * compliant with Android 13-16 (API 33-36) PendingIntent security requirements.
 */
object WidgetActions {
    const val ACTION_PLAY_PAUSE = "com.xperience.player.widget.ACTION_PLAY_PAUSE"
    const val ACTION_NEXT = "com.xperience.player.widget.ACTION_NEXT"
    const val ACTION_PREV = "com.xperience.player.widget.ACTION_PREV"
    const val ACTION_TOGGLE_FAVORITE = "com.xperience.player.widget.ACTION_TOGGLE_FAVORITE"
    const val ACTION_REFRESH_RECOMMENDATIONS = "com.xperience.player.widget.ACTION_REFRESH_RECOMMENDATIONS"

    const val ACTION_OPEN_PLAYER = "com.xperience.player.widget.ACTION_OPEN_PLAYER"
    const val ACTION_OPEN_AMBIENT = "com.xperience.player.widget.ACTION_OPEN_AMBIENT"
    const val ACTION_PLAY_PLAYLIST = "com.xperience.player.widget.ACTION_PLAY_PLAYLIST"
    const val ACTION_OPEN_HOME = "com.xperience.player.widget.ACTION_OPEN_HOME"

    private const val REQ_PLAY_PAUSE = 4101
    private const val REQ_NEXT = 4102
    private const val REQ_PREV = 4103
    private const val REQ_TOGGLE_FAVORITE = 4104
    private const val REQ_REFRESH_RECOMMENDATIONS = 4105
    private const val REQ_OPEN_PLAYER = 4201
    private const val REQ_OPEN_AMBIENT = 4202
    private const val REQ_OPEN_HOME = 4203
    private const val REQ_OPEN_REFRESH_RECOMMENDATIONS = 4204
    private const val REQ_PLAY_PLAYLIST_BASE = 4300

    private const val IMMUTABLE_UPDATE_FLAGS =
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun playbackBroadcastIntent(context: Context, action: String): Intent {
        val appContext = context.applicationContext
        return Intent(action).apply {
            component = ComponentName(appContext, WidgetActionReceiver::class.java)
            `package` = appContext.packageName
        }
    }

    fun playPausePendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQ_PLAY_PAUSE,
            playbackBroadcastIntent(context, ACTION_PLAY_PAUSE),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun nextPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQ_NEXT,
            playbackBroadcastIntent(context, ACTION_NEXT),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun previousPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQ_PREV,
            playbackBroadcastIntent(context, ACTION_PREV),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun toggleFavoritePendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQ_TOGGLE_FAVORITE,
            playbackBroadcastIntent(context, ACTION_TOGGLE_FAVORITE),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun refreshRecommendationsPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            REQ_REFRESH_RECOMMENDATIONS,
            playbackBroadcastIntent(context, ACTION_REFRESH_RECOMMENDATIONS),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun openPlayerPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_PLAYER,
            WidgetDeepLink.createIntent(context, WidgetDeepLink.OpenPlayer),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun openAmbientPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_AMBIENT,
            WidgetDeepLink.createIntent(context, WidgetDeepLink.OpenAmbient),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun openHomePendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_HOME,
            WidgetDeepLink.createIntent(context, WidgetDeepLink.OpenHome),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun openRefreshRecommendationsPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_REFRESH_RECOMMENDATIONS,
            WidgetDeepLink.createIntent(context, WidgetDeepLink.RefreshRecommendations),
            IMMUTABLE_UPDATE_FLAGS
        )
    }

    fun openPlaylistPendingIntent(
        context: Context,
        item: WidgetPlaylistItem,
        slotIndex: Int,
        autoPlay: Boolean = true
    ): PendingIntent {
        val deepLink = WidgetDeepLink.OpenPlaylist(
            id = item.id,
            title = item.title,
            sourceType = item.sourceType,
            ytmPlaylistId = item.playlistId,
            browseId = item.browseId,
            seedVideoId = item.seedVideoId,
            params = item.params,
            autoPlay = autoPlay
        )
        val intent = WidgetDeepLink.createIntent(context, deepLink)
        // Unique request code per slot + stable hash ensures launcher never coalesces sibling cards.
        val requestCode = REQ_PLAY_PLAYLIST_BASE +
            (slotIndex.coerceIn(0, 15) * 100) +
            ((item.id.hashCode() and 0x7FFF) % 97)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            IMMUTABLE_UPDATE_FLAGS
        )
    }
}

/**
 * Typed deep-link contract between home-screen widgets and [MainActivity].
 */
sealed interface WidgetDeepLink {
    data object OpenPlayer : WidgetDeepLink
    data object OpenAmbient : WidgetDeepLink
    data object OpenHome : WidgetDeepLink
    data object RefreshRecommendations : WidgetDeepLink

    data class OpenPlaylist(
        val id: String,
        val title: String,
        val sourceType: WidgetPlaylistSourceType,
        val ytmPlaylistId: String? = null,
        val browseId: String? = null,
        val seedVideoId: String? = null,
        val params: String? = null,
        val autoPlay: Boolean = true
    ) : WidgetDeepLink

    companion object {
        const val EXTRA_DEEP_LINK_TYPE = "com.xperience.player.widget.EXTRA_DEEP_LINK_TYPE"
        const val EXTRA_PLAYLIST_ID = "com.xperience.player.widget.EXTRA_PLAYLIST_ID"
        const val EXTRA_PLAYLIST_TITLE = "com.xperience.player.widget.EXTRA_PLAYLIST_TITLE"
        const val EXTRA_PLAYLIST_SOURCE_TYPE = "com.xperience.player.widget.EXTRA_PLAYLIST_SOURCE_TYPE"
        const val EXTRA_YTM_PLAYLIST_ID = "com.xperience.player.widget.EXTRA_YTM_PLAYLIST_ID"
        const val EXTRA_YTM_BROWSE_ID = "com.xperience.player.widget.EXTRA_YTM_BROWSE_ID"
        const val EXTRA_RADIO_SEED_VIDEO_ID = "com.xperience.player.widget.EXTRA_RADIO_SEED_VIDEO_ID"
        const val EXTRA_RADIO_PARAMS = "com.xperience.player.widget.EXTRA_RADIO_PARAMS"
        const val EXTRA_AUTO_PLAY = "com.xperience.player.widget.EXTRA_AUTO_PLAY"

        const val TYPE_OPEN_PLAYER = "open_player"
        const val TYPE_OPEN_AMBIENT = "open_ambient"
        const val TYPE_OPEN_HOME = "open_home"
        const val TYPE_REFRESH = "refresh_recommendations"
        const val TYPE_OPEN_PLAYLIST = "open_playlist"

        fun createIntent(context: Context, deepLink: WidgetDeepLink): Intent {
            val appContext = context.applicationContext
            val action = when (deepLink) {
                OpenPlayer -> WidgetActions.ACTION_OPEN_PLAYER
                OpenAmbient -> WidgetActions.ACTION_OPEN_AMBIENT
                OpenHome -> WidgetActions.ACTION_OPEN_HOME
                RefreshRecommendations -> WidgetActions.ACTION_REFRESH_RECOMMENDATIONS
                is OpenPlaylist -> WidgetActions.ACTION_PLAY_PLAYLIST
            }
            return Intent(appContext, MainActivity::class.java).apply {
                this.action = action
                `package` = appContext.packageName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

                when (deepLink) {
                    OpenPlayer -> {
                        putExtra(EXTRA_DEEP_LINK_TYPE, TYPE_OPEN_PLAYER)
                        data = Uri.parse("xperience://widget/player")
                    }
                    OpenAmbient -> {
                        putExtra(EXTRA_DEEP_LINK_TYPE, TYPE_OPEN_AMBIENT)
                        data = Uri.parse("xperience://widget/ambient")
                    }
                    OpenHome -> {
                        putExtra(EXTRA_DEEP_LINK_TYPE, TYPE_OPEN_HOME)
                        data = Uri.parse("xperience://widget/home")
                    }
                    RefreshRecommendations -> {
                        putExtra(EXTRA_DEEP_LINK_TYPE, TYPE_REFRESH)
                        data = Uri.parse("xperience://widget/refresh")
                    }
                    is OpenPlaylist -> {
                        putExtra(EXTRA_DEEP_LINK_TYPE, TYPE_OPEN_PLAYLIST)
                        putExtra(EXTRA_PLAYLIST_ID, deepLink.id)
                        putExtra(EXTRA_PLAYLIST_TITLE, deepLink.title)
                        putExtra(EXTRA_PLAYLIST_SOURCE_TYPE, deepLink.sourceType.wireValue)
                        putExtra(EXTRA_YTM_PLAYLIST_ID, deepLink.ytmPlaylistId)
                        putExtra(EXTRA_YTM_BROWSE_ID, deepLink.browseId)
                        putExtra(EXTRA_RADIO_SEED_VIDEO_ID, deepLink.seedVideoId)
                        putExtra(EXTRA_RADIO_PARAMS, deepLink.params)
                        putExtra(EXTRA_AUTO_PLAY, deepLink.autoPlay)
                        data = Uri.Builder()
                            .scheme("xperience")
                            .authority("widget")
                            .appendPath("playlist")
                            .appendPath(deepLink.sourceType.wireValue)
                            .appendPath(deepLink.id)
                            .build()
                    }
                }
            }
        }

        /**
         * Parses an incoming [MainActivity] intent into a typed [WidgetDeepLink], or returns `null`
         * if the intent did not originate from an Xperience widget.
         */
        fun parse(intent: Intent?): WidgetDeepLink? {
            if (intent == null) return null
            val extras = intent.extras
            return parseRaw(
                action = intent.action,
                deepLinkType = extras?.getString(EXTRA_DEEP_LINK_TYPE),
                playlistId = extras?.getString(EXTRA_PLAYLIST_ID),
                playlistTitle = extras?.getString(EXTRA_PLAYLIST_TITLE),
                sourceTypeRaw = extras?.getString(EXTRA_PLAYLIST_SOURCE_TYPE),
                ytmPlaylistId = extras?.getString(EXTRA_YTM_PLAYLIST_ID),
                browseId = extras?.getString(EXTRA_YTM_BROWSE_ID),
                seedVideoId = extras?.getString(EXTRA_RADIO_SEED_VIDEO_ID),
                params = extras?.getString(EXTRA_RADIO_PARAMS),
                autoPlay = extras?.getBoolean(EXTRA_AUTO_PLAY, true) ?: true
            )
        }

        /**
         * Pure JVM-testable parser backing [parse].
         */
        fun parseRaw(
            action: String?,
            deepLinkType: String? = null,
            playlistId: String? = null,
            playlistTitle: String? = null,
            sourceTypeRaw: String? = null,
            ytmPlaylistId: String? = null,
            browseId: String? = null,
            seedVideoId: String? = null,
            params: String? = null,
            autoPlay: Boolean = true
        ): WidgetDeepLink? {
            val resolvedType = deepLinkType?.trim()?.takeIf { it.isNotEmpty() } ?: when (action) {
                WidgetActions.ACTION_OPEN_PLAYER -> TYPE_OPEN_PLAYER
                WidgetActions.ACTION_OPEN_AMBIENT -> TYPE_OPEN_AMBIENT
                WidgetActions.ACTION_OPEN_HOME -> TYPE_OPEN_HOME
                WidgetActions.ACTION_REFRESH_RECOMMENDATIONS -> TYPE_REFRESH
                WidgetActions.ACTION_PLAY_PLAYLIST -> TYPE_OPEN_PLAYLIST
                else -> return null
            }

            return when (resolvedType) {
                TYPE_OPEN_PLAYER -> OpenPlayer
                TYPE_OPEN_AMBIENT -> OpenAmbient
                TYPE_OPEN_HOME -> OpenHome
                TYPE_REFRESH -> RefreshRecommendations
                TYPE_OPEN_PLAYLIST -> {
                    val cleanId = playlistId?.trim().orEmpty()
                    val cleanYtmId = ytmPlaylistId?.trim()?.ifBlank { null }
                    val effectiveId = cleanId.ifBlank { cleanYtmId.orEmpty() }
                    if (effectiveId.isBlank()) return OpenHome
                    OpenPlaylist(
                        id = effectiveId,
                        title = playlistTitle?.trim().orEmpty(),
                        sourceType = WidgetPlaylistSourceType.fromWireValue(sourceTypeRaw),
                        ytmPlaylistId = cleanYtmId,
                        browseId = browseId?.trim()?.ifBlank { null },
                        seedVideoId = seedVideoId?.trim()?.ifBlank { null },
                        params = params?.trim()?.ifBlank { null },
                        autoPlay = autoPlay
                    )
                }
                else -> null
            }
        }

        /**
         * Strips widget deep-link extras from [intent] after consumption so configuration changes
         * or activity recreations never replay the same navigation action.
         */
        fun clearFromIntent(intent: Intent?) {
            if (intent == null) return
            intent.removeExtra(EXTRA_DEEP_LINK_TYPE)
            intent.removeExtra(EXTRA_PLAYLIST_ID)
            intent.removeExtra(EXTRA_PLAYLIST_TITLE)
            intent.removeExtra(EXTRA_PLAYLIST_SOURCE_TYPE)
            intent.removeExtra(EXTRA_YTM_PLAYLIST_ID)
            intent.removeExtra(EXTRA_YTM_BROWSE_ID)
            intent.removeExtra(EXTRA_RADIO_SEED_VIDEO_ID)
            intent.removeExtra(EXTRA_RADIO_PARAMS)
            intent.removeExtra(EXTRA_AUTO_PLAY)
            if (intent.action?.startsWith("com.xperience.player.widget.") == true) {
                intent.action = Intent.ACTION_MAIN
            }
        }
    }
}
