package com.jellyfinmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jellyfinmusic.MainActivity
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.PlaybackReporter
import com.jellyfinmusic.data.PlaybackStateStore
import com.jellyfinmusic.data.SavedQueue
import com.jellyfinmusic.data.SavedTrack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground MediaSessionService that owns the ExoPlayer instance.
 *
 * Keeping the player inside a service — rather than in an Activity or ViewModel —
 * is what allows audio to survive the screen locking, the app being backgrounded,
 * or the task being swiped away, and is what publishes the lock-screen and
 * notification transport controls.
 *
 * The service also persists the queue, so closing the app and reopening it
 * resumes on the same track at the same position.
 */
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject lateinit var playbackState: PlaybackStateStore

    @Inject lateinit var repo: JellyfinRepository

    @Inject lateinit var reporter: PlaybackReporter

    private var mediaSession: MediaSession? = null

    private val handler = Handler(Looper.getMainLooper())

    /** Position moves continuously, so it is checkpointed on a timer while playing. */
    private val positionSaver = object : Runnable {
        override fun run() {
            val player = mediaSession?.player
            if (player?.isPlaying == true) {
                saveState()
                player.currentMediaItem?.let {
                    reporter.onProgress(it.mediaId, player.currentPosition, isPaused = false)
                }
            }
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // Track changes, play/pause and queue edits all change what needs
            // restoring; position alone is handled by the timer.
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY
                )
            ) {
                saveState()
            }
            reportPlaybackChange(player, events)
        }
    }

    /**
     * Mirrors transport changes to the server: a new track opens a play, and
     * pausing or resuming updates it. Progress while playing is handled by the
     * same timer that checkpoints the queue.
     */
    private fun reportPlaybackChange(player: Player, events: Player.Events) {
        val itemId = player.currentMediaItem?.mediaId ?: return
        when {
            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ->
                reporter.onStart(itemId, player.currentPosition)

            events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED) ->
                if (player.isPlaying) {
                    reporter.onStart(itemId, player.currentPosition)
                } else {
                    reporter.onProgress(itemId, player.currentPosition, isPaused = true)
                }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            // Pause instead of continuing to play into a room when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        restoreState(player)
        player.addListener(listener)
        handler.postDelayed(positionSaver, SAVE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        val player = mediaSession?.player
        // Swiping the app away while paused should tear the service down; while
        // playing, playback continues in the background as the user expects.
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveState()
        mediaSession?.player?.let { reporter.onStop(it.currentMediaItem?.mediaId, it.currentPosition) }
        handler.removeCallbacks(positionSaver)
        mediaSession?.run {
            player.removeListener(listener)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Loads the previous queue paused and seeked to where it left off. Playback
     * is never resumed automatically — reopening the app should not start
     * making noise on its own.
     */
    private fun restoreState(player: Player) {
        val saved = playbackState.load() ?: return
        val items = saved.tracks.map { track ->
            PlayableTrack(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                // Rebuilt now, because the saved session's token may be stale.
                streamUrl = repo.streamUrl(track.id),
                artworkUrl = track.artworkUrl
            ).toMediaItem()
        }
        if (items.isEmpty()) return

        val index = saved.currentIndex.coerceIn(0, items.lastIndex)
        player.setMediaItems(items, index, saved.positionMs.coerceAtLeast(0L))
        player.shuffleModeEnabled = saved.shuffleEnabled
        player.repeatMode = saved.repeatMode
        player.playWhenReady = false
        player.prepare()
    }

    private fun saveState() {
        val player = mediaSession?.player ?: return
        if (player.mediaItemCount == 0) {
            playbackState.clear()
            return
        }
        val tracks = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            val metadata = item.mediaMetadata
            SavedTrack(
                id = item.mediaId,
                title = metadata.title?.toString().orEmpty(),
                artist = metadata.artist?.toString().orEmpty(),
                album = metadata.albumTitle?.toString().orEmpty(),
                artworkUrl = metadata.artworkUri?.toString()
            )
        }
        playbackState.save(
            SavedQueue(
                tracks = tracks,
                currentIndex = player.currentMediaItemIndex,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode
            )
        )
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
