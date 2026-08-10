package com.jellyfinmusic.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the UI to [MusicService] via a MediaController and mirrors the
 * player's state into flows Compose can observe. The service, not this class,
 * owns playback — this is only a remote control.
 */
@Singleton
class PlayerConnection @Inject constructor(
    private val context: Context
) {
    private val handler = Handler(Looper.getMainLooper())

    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncState()
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            controller?.let { c ->
                if (c.isPlaying) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition,
                        durationMs = c.duration.coerceAtLeast(0L)
                    )
                }
            }
            handler.postDelayed(this, 500L)
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = future.get().also { it.addListener(listener) }
            syncState()
            handler.post(progressTicker)
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        handler.removeCallbacks(progressTicker)
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /** Replaces the queue with [tracks] and starts at [startIndex]. */
    fun playQueue(tracks: List<PlayableTrack>, startIndex: Int) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        c.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun addToQueue(track: PlayableTrack) {
        controller?.addMediaItem(track.toMediaItem())
    }

    /** Inserts directly after the current track rather than at the end. */
    fun playNext(track: PlayableTrack) {
        val c = controller ?: return
        val insertAt = (c.currentMediaItemIndex + 1).coerceIn(0, c.mediaItemCount)
        c.addMediaItem(insertAt, track.toMediaItem())
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit

    fun previous() {
        val c = controller ?: return
        // Restart the current track when more than a few seconds in, matching
        // the behaviour of every other music player.
        if (c.currentPosition > 3_000L) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun skipToQueueItem(index: Int) {
        controller?.seekTo(index, 0L)
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    // ---- Sleep timer ------------------------------------------------------

    private val _sleepTimerEndsAt = MutableStateFlow<Long?>(null)
    val sleepTimerEndsAt: StateFlow<Long?> = _sleepTimerEndsAt.asStateFlow()

    private val sleepRunnable = Runnable {
        controller?.pause()
        _sleepTimerEndsAt.value = null
    }

    /** Pauses playback after [minutes]; passing 0 cancels a running timer. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes <= 0) {
            _sleepTimerEndsAt.value = null
            return
        }
        val delayMs = minutes * 60_000L
        _sleepTimerEndsAt.value = System.currentTimeMillis() + delayMs
        handler.postDelayed(sleepRunnable, delayMs)
    }

    private fun syncState() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).map { index ->
            val md = c.getMediaItemAt(index).mediaMetadata
            QueueEntry(
                index = index,
                title = md.title?.toString().orEmpty(),
                artist = md.artist?.toString().orEmpty(),
                artworkUrl = md.artworkUri?.toString()
            )
        }
        _state.value = PlayerUiState(
            isConnected = true,
            isPlaying = c.isPlaying,
            currentItemId = c.currentMediaItem?.mediaId,
            currentIndex = c.currentMediaItemIndex.takeIf { c.mediaItemCount > 0 } ?: -1,
            title = c.mediaMetadata.title?.toString().orEmpty(),
            artist = c.mediaMetadata.artist?.toString().orEmpty(),
            album = c.mediaMetadata.albumTitle?.toString().orEmpty(),
            artworkUrl = c.mediaMetadata.artworkUri?.toString(),
            positionMs = c.currentPosition,
            durationMs = c.duration.coerceAtLeast(0L),
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue
        )
    }
}

data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    /** Jellyfin item ID of the current track, used for liking from the player. */
    val currentItemId: String? = null,
    val currentIndex: Int = -1,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList()
) {
    val hasTrack: Boolean get() = currentIndex >= 0 && title.isNotBlank()
}

data class QueueEntry(
    val index: Int,
    val title: String,
    val artist: String,
    val artworkUrl: String?
)

data class PlayableTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val streamUrl: String,
    val artworkUrl: String?
) {
    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUrl?.let { android.net.Uri.parse(it) })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
}
