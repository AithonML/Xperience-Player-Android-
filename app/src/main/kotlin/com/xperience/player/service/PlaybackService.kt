package com.xperience.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.xperience.player.MainActivity
import com.xperience.player.R
import com.xperience.player.data.FavoritesRepository
import com.xperience.player.domain.model.PlaybackRepeatMode
import com.xperience.player.widget.WidgetSyncBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service backing the native Android SystemUI Media Controls.
 * Manages the MediaSession, notification channel, custom actions (Shuffle, Repeat),
 * and media routing for Android 13+ (wavy seekbar, dynamic cover, output switcher).
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService(), MediaSessionService.Listener {

    private var mediaSession: MediaSession? = null
    private lateinit var playerService: AudioPlayerService
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var favoritesRepository: FavoritesRepository? = null
    private var lastSyncedBackgroundTrackId: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "PlaybackService onCreate")

        setListener(this)
        createNotificationChannel()

        playerService = AudioPlayerService.getInstance(applicationContext)

        // Configure notification provider BEFORE adding any session
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.playback_channel_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
            .apply {
                setSmallIcon(R.drawable.media3_notification_small_icon)
            }
        setMediaNotificationProvider(notificationProvider)

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)

                val sessionCommands = connectionResult.availableSessionCommands.buildUpon()
                    .add(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                    .build()

                val playerCommands = connectionResult.availablePlayerCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SET_SHUFFLE_MODE)
                    .add(Player.COMMAND_SET_REPEAT_MODE)
                    .build()

                return MediaSession.ConnectionResult.accept(sessionCommands, playerCommands)
            }

            override fun onPostConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ) {
                super.onPostConnect(session, controller)
                updateCustomLayout()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_SHUFFLE -> {
                        playerService.toggleShuffle()
                        updateCustomLayout()
                    }
                    ACTION_REPEAT -> {
                        playerService.cycleRepeatMode()
                        updateCustomLayout()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        val session = MediaSession.Builder(this, playerService.forwardingPlayer)
            .setCallback(sessionCallback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
        mediaSession = session

        // Register the session with MediaSessionService so MediaNotificationManager binds to it
        addSession(session)
        playerService.attachMediaSession(session)
        updateCustomLayout()

        // Keep Home Screen widgets live-synced during headless background playback (when UI ViewModel is not attached)
        serviceScope.launch(Dispatchers.IO) {
            val repo = FavoritesRepository(applicationContext)
            favoritesRepository = repo
            playerService.playbackState.collect { state ->
                if (WidgetSyncBridge.onFavoriteToggleRequested != null) {
                    return@collect
                }
                val trackId = state.currentTrack?.id
                if (trackId != lastSyncedBackgroundTrackId) {
                    lastSyncedBackgroundTrackId = trackId
                    runCatching { repo.loadFavoritesFromDisk() }
                }
                val isLiked = trackId != null &&
                    repo.likedTrackIds.value.contains(trackId)
                WidgetSyncBridge.syncPlayback(
                    context = applicationContext,
                    playbackState = state,
                    isLiked = isLiked
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        mediaSession?.let { session ->
            if (!isSessionAdded(session)) {
                addSession(session)
            }
        }
        return result
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.playback_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
            Log.d(TAG, "NotificationChannel '$CHANNEL_ID' verified/created.")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onForegroundServiceStartNotAllowedException() {
        Log.w(TAG, "ForegroundServiceStartNotAllowedException handled gracefully")
    }

    fun updateCustomLayout() {
        val session = mediaSession ?: return
        val buttons = playerService.buildCustomButtons()
        session.setMediaButtonPreferences(buttons)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing()) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "PlaybackService onDestroy")
        serviceScope.cancel()
        favoritesRepository = null
        playerService.detachMediaSession()
        mediaSession?.let { session ->
            if (isSessionAdded(session)) {
                removeSession(session)
            }
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val CHANNEL_ID = "xperience_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SHUFFLE = "com.xperience.player.ACTION_SHUFFLE"
        const val ACTION_REPEAT = "com.xperience.player.ACTION_REPEAT"
    }
}
