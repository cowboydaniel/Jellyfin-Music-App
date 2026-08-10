package com.jellyfinmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jellyfinmusic.MainActivity
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.PlaybackReporter
import com.jellyfinmusic.data.PlaybackStateStore
import com.jellyfinmusic.data.SavedQueue
import com.jellyfinmusic.data.SavedTrack
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
class MusicService : MediaLibraryService() {

    @Inject lateinit var playbackState: PlaybackStateStore

    @Inject lateinit var repo: JellyfinRepository

    @Inject lateinit var reporter: PlaybackReporter

    @Inject lateinit var cacheDataSourceFactory: androidx.media3.datasource.cache.CacheDataSource.Factory

    private var mediaSession: MediaLibrarySession? = null

    private val serviceScope =
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

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
            // Reads downloaded tracks off disk and streams everything else, so
            // offline playback needs no separate path.
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheDataSourceFactory)
            )
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

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .build()

        restoreState(player)
        player.addListener(listener)
        handler.postDelayed(positionSaver, SAVE_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    /**
     * Exposes the library as a browsable tree, which is what Android Auto and
     * other media browsers navigate. Everything is fetched on demand; nothing
     * is cached here beyond what the repository already holds.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(browsableItem(ROOT_ID, "Jellyfin Music"), params)
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = com.google.common.util.concurrent.SettableFuture
                .create<LibraryResult<ImmutableList<MediaItem>>>()
            serviceScope.launch {
                val children = runCatching { childrenOf(parentId) }.getOrDefault(emptyList())
                future.set(LibraryResult.ofItemList(ImmutableList.copyOf(children), params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = com.google.common.util.concurrent.SettableFuture
                .create<LibraryResult<MediaItem>>()
            serviceScope.launch {
                val item = runCatching { repo.itemById(mediaId) }.getOrNull()
                future.set(
                    if (item == null) {
                        LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                    } else {
                        LibraryResult.ofItem(item.toPlayableMediaItem(), null)
                    }
                )
            }
            return future
        }

        /**
         * A browser hands back the item it wants played; the queue is filled
         * from its siblings so skipping works in the car.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = com.google.common.util.concurrent.SettableFuture
                .create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val resolved = mediaItems.map { requested ->
                    val existing = requested.localConfiguration?.uri
                    if (existing != null) {
                        requested
                    } else {
                        // Browser items carry only an ID, so the stream URL is
                        // attached before handing them to the player.
                        val item = runCatching { repo.itemById(requested.mediaId) }.getOrNull()
                        item?.toPlayableMediaItem() ?: requested
                    }
                }
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        resolved,
                        startIndex,
                        startPositionMs
                    )
                )
            }
            return future
        }
    }

    private suspend fun childrenOf(parentId: String): List<MediaItem> = when {
        parentId == ROOT_ID -> listOf(
            browsableItem(NODE_PLAYLISTS, "Playlists"),
            browsableItem(NODE_ALBUMS, "Albums"),
            browsableItem(NODE_ARTISTS, "Artists"),
            browsableItem(NODE_LIKED, "Liked songs")
        )

        parentId == NODE_PLAYLISTS -> repo.playlists().map { it.toBrowsableItem() }
        parentId == NODE_ALBUMS -> repo.allAlbums(limit = 200).map { it.toBrowsableItem() }
        parentId == NODE_ARTISTS -> repo.topArtists(limit = 200).map { it.toBrowsableItem() }
        parentId == NODE_LIKED -> repo.favoriteSongs().map { it.toPlayableMediaItem() }

        parentId.startsWith(PREFIX_PLAYLIST) ->
            repo.playlistTracks(parentId.removePrefix(PREFIX_PLAYLIST))
                .map { it.toPlayableMediaItem() }

        parentId.startsWith(PREFIX_ALBUM) ->
            repo.tracksOfAlbum(parentId.removePrefix(PREFIX_ALBUM))
                .map { it.toPlayableMediaItem() }

        parentId.startsWith(PREFIX_ARTIST) ->
            repo.albumsOfArtist(parentId.removePrefix(PREFIX_ARTIST))
                .map { it.toBrowsableItem() }

        else -> emptyList()
    }

    private fun browsableItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun com.jellyfinmusic.network.BaseItem.toBrowsableItem(): MediaItem {
        val prefix = when (type) {
            "Playlist" -> PREFIX_PLAYLIST
            "MusicArtist" -> PREFIX_ARTIST
            else -> PREFIX_ALBUM
        }
        return MediaItem.Builder()
            .setMediaId(prefix + id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name.orEmpty())
                    .setSubtitle(artistName.orEmpty())
                    .setArtworkUri(repo.artworkFor(this)?.let(android.net.Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    private fun com.jellyfinmusic.network.BaseItem.toPlayableMediaItem(): MediaItem =
        PlayableTrack(
            id = id,
            title = name.orEmpty(),
            artist = artistName.orEmpty(),
            album = album.orEmpty(),
            streamUrl = repo.streamUrl(id),
            artworkUrl = repo.artworkFor(this)
        ).toMediaItem()

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
        const val ROOT_ID = "root"
        const val NODE_PLAYLISTS = "node_playlists"
        const val NODE_ALBUMS = "node_albums"
        const val NODE_ARTISTS = "node_artists"
        const val NODE_LIKED = "node_liked"
        const val PREFIX_PLAYLIST = "playlist:"
        const val PREFIX_ALBUM = "album:"
        const val PREFIX_ARTIST = "artist:"
    }
}
