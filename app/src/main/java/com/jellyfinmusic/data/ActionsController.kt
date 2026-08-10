package com.jellyfinmusic.data

import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayableTrack
import com.jellyfinmusic.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Which sheet, if any, the action layer is currently showing. */
sealed interface ActionSheet {
    data object None : ActionSheet

    /** The overflow menu for a single track. */
    data class TrackMenu(val item: BaseItem, val playlistContext: PlaylistContext? = null) : ActionSheet

    /** The playlist picker, reached from the track menu. */
    data class AddToPlaylist(val item: BaseItem) : ActionSheet

    /** Name entry for a new playlist; [seedItem] becomes its first track. */
    data class CreatePlaylist(val seedItem: BaseItem?) : ActionSheet

    /** Actions on a playlist itself rather than a track inside it. */
    data class PlaylistMenu(val playlist: BaseItem) : ActionSheet

    /** Rename entry for an existing playlist. */
    data class RenamePlaylist(val playlist: BaseItem) : ActionSheet
}

/** A share sheet payload, handed out for the host to fire as an intent. */
data class SharePayload(val title: String, val text: String)

/** Somewhere the action layer wants the app to navigate to. */
sealed interface NavTarget {
    data class Album(val id: String, val name: String) : NavTarget
    data class Artist(val id: String, val name: String) : NavTarget
}

/**
 * Identifies the playlist a track is being shown from, which is what makes
 * "Remove from this playlist" possible.
 */
data class PlaylistContext(val playlistId: String, val playlistItemId: String?)

/**
 * Shared home for actions that can be triggered from any screen — the track
 * overflow menu, adding to playlists, liking. Holding this in one singleton
 * keeps a single sheet on screen and one source of truth for the playlist list,
 * rather than each screen growing its own copy.
 */
@androidx.media3.common.util.UnstableApi
@Singleton
class ActionsController @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection,
    private val downloads: DownloadsController,
    private val dismissed: DismissedStore
) {
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    private val _sheet = MutableStateFlow<ActionSheet>(ActionSheet.None)
    val sheet: StateFlow<ActionSheet> = _sheet.asStateFlow()

    private val _playlists = MutableStateFlow<List<BaseItem>>(emptyList())
    val playlists: StateFlow<List<BaseItem>> = _playlists.asStateFlow()

    private val _navigation = kotlinx.coroutines.flow.MutableSharedFlow<NavTarget>(extraBufferCapacity = 4)
    val navigation = _navigation.asSharedFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** Bumped whenever a playlist changes, so open screens know to reload. */
    private val _playlistRevision = MutableStateFlow(0)
    val playlistRevision: StateFlow<Int> = _playlistRevision.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> get() = repo.favoriteIds

    val dislikedIds: StateFlow<Set<String>> get() = repo.dislikedIds

    /**
     * Share requests. Firing an intent needs a Context, which this layer does
     * not hold, so the request travels out to whoever is hosting the UI.
     */
    private val _share = kotlinx.coroutines.flow.MutableSharedFlow<SharePayload>(extraBufferCapacity = 4)
    val share = _share.asSharedFlow()

    fun shareItem(item: BaseItem) {
        dismissSheet()
        _share.tryEmit(
            SharePayload(
                title = item.name.orEmpty(),
                text = buildString {
                    append(item.name.orEmpty())
                    item.artistName?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
                    append("\n").append(repo.shareUrl(item.id))
                }
            )
        )
    }

    fun toggleDislike(item: BaseItem) = toggleDislikeById(item.id)

    /** Hides an item from the Home shelves. Local only — Jellyfin has no such flag. */
    fun notInterested(item: BaseItem) {
        dismissed.dismiss(item.id)
        dismissSheet()
        _toast.value = "Hidden from Home"
    }

    fun toggleDislikeById(itemId: String) {
        scope.launch {
            val nowDisliked = !repo.isDisliked(itemId)
            repo.toggleDislike(itemId)
                .onSuccess {
                    _toast.value = if (nowDisliked) "Marked as disliked" else "Rating cleared"
                }
                .onFailure { _toast.value = it.message ?: "Could not save the rating" }
        }
    }

    val downloadStates: StateFlow<Map<String, DownloadState>> get() = downloads.states

    fun downloadState(itemId: String) = downloads.stateOf(itemId)

    fun toggleDownload(item: BaseItem) {
        if (downloads.stateOf(item.id) == DownloadState.NONE) {
            downloads.download(item)
            _toast.value = "Downloading \"${item.name.orEmpty()}\""
        } else {
            downloads.remove(item.id)
            _toast.value = "Download removed"
        }
    }

    /** Downloads a whole album or playlist in one go. */
    fun downloadAll(items: List<BaseItem>, label: String) {
        if (items.isEmpty()) return
        downloads.downloadAll(items)
        _toast.value = "Downloading $label (${items.size} tracks)"
    }

    fun showTrackMenu(item: BaseItem, playlistContext: PlaylistContext? = null) {
        _sheet.value = ActionSheet.TrackMenu(item, playlistContext)
    }

    fun showAddToPlaylist(item: BaseItem) {
        _sheet.value = ActionSheet.AddToPlaylist(item)
        refreshPlaylists()
    }

    fun showPlaylistMenu(playlist: BaseItem) {
        _sheet.value = ActionSheet.PlaylistMenu(playlist)
    }

    fun showRenamePlaylist(playlist: BaseItem) {
        _sheet.value = ActionSheet.RenamePlaylist(playlist)
    }

    fun renamePlaylist(playlist: BaseItem, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            runCatching { repo.renamePlaylist(playlist.id, trimmed) }
                .onSuccess {
                    _toast.value = "Renamed to \"$trimmed\""
                    _playlistRevision.value++
                    refreshPlaylists()
                    dismissSheet()
                }
                .onFailure { _toast.value = it.message ?: "Could not rename the playlist" }
        }
    }

    /** Asks the host to open an image picker for [playlist]'s cover. */
    private val _pickImageFor = kotlinx.coroutines.flow.MutableSharedFlow<BaseItem>(extraBufferCapacity = 2)
    val pickImageFor = _pickImageFor.asSharedFlow()

    fun requestCoverArt(playlist: BaseItem) {
        dismissSheet()
        _pickImageFor.tryEmit(playlist)
    }

    fun setCoverArt(playlistId: String, jpegBytes: ByteArray) {
        scope.launch {
            runCatching { repo.uploadPrimaryImage(playlistId, jpegBytes) }
                .onSuccess {
                    _toast.value = "Cover updated"
                    _playlistRevision.value++
                    refreshPlaylists()
                }
                .onFailure { _toast.value = it.message ?: "Could not update the cover" }
        }
    }

    fun showCreatePlaylist(seedItem: BaseItem?) {
        _sheet.value = ActionSheet.CreatePlaylist(seedItem)
    }

    fun dismissSheet() {
        _sheet.value = ActionSheet.None
    }

    /** Shows a message through the shared snackbar. */
    fun notify(message: String) {
        _toast.value = message
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun clearUserState() {
        downloads.clearForSignOut()
        _playlists.value = emptyList()
        _sheet.value = ActionSheet.None
    }

    fun refreshPlaylists() {
        scope.launch {
            runCatching { repo.playlists() }.onSuccess { _playlists.value = it }
        }
    }

    fun toggleFavorite(item: BaseItem) {
        scope.launch {
            val nowFavorite = !repo.isFavorite(item.id)
            repo.toggleFavorite(item.id)
                .onSuccess {
                    _toast.value = if (nowFavorite) "Added to Liked songs" else "Removed from Liked songs"
                }
                .onFailure { _toast.value = it.message ?: "Could not update Liked songs" }
        }
    }

    /** Likes by ID, for the player where only the media ID is to hand. */
    fun toggleFavoriteById(itemId: String) {
        scope.launch {
            val nowFavorite = !repo.isFavorite(itemId)
            repo.toggleFavorite(itemId)
                .onSuccess {
                    _toast.value = if (nowFavorite) "Added to Liked songs" else "Removed from Liked songs"
                }
                .onFailure { _toast.value = it.message ?: "Could not update Liked songs" }
        }
    }

    /** Opens the album a track belongs to, if the server reported one. */
    fun goToAlbum(item: BaseItem) {
        val albumId = item.albumId
        if (albumId.isNullOrBlank()) {
            _toast.value = "No album for this track"
            return
        }
        dismissSheet()
        _navigation.tryEmit(NavTarget.Album(albumId, item.album.orEmpty()))
    }

    fun goToArtist(item: BaseItem) {
        val artist = item.artistItems.firstOrNull { !it.id.isNullOrBlank() }
        if (artist?.id == null) {
            _toast.value = "No artist for this track"
            return
        }
        dismissSheet()
        _navigation.tryEmit(NavTarget.Artist(artist.id, artist.name.orEmpty()))
    }

    /** Server-generated radio seeded from this track. */
    fun startMix(item: BaseItem) {
        dismissSheet()
        scope.launch {
            val mix = runCatching { repo.instantMix(item.id) }.getOrDefault(emptyList())
            if (mix.isEmpty()) {
                _toast.value = "No mix available for this track"
                return@launch
            }
            player.playQueue(mix.map { it.toTrack() }, 0)
            _toast.value = "Started a mix"
        }
    }

    fun playNext(item: BaseItem) {
        player.playNext(item.toTrack())
        _toast.value = "Playing next"
    }

    fun addToQueue(item: BaseItem) {
        player.addToQueue(item.toTrack())
        _toast.value = "Added to queue"
    }

    fun addToPlaylist(playlist: BaseItem, item: BaseItem) {
        scope.launch {
            runCatching { repo.addToPlaylist(playlist.id, listOf(item.id)) }
                .onSuccess {
                    _toast.value = "Added to ${playlist.name.orEmpty()}"
                    _playlistRevision.value++
                    dismissSheet()
                }
                .onFailure { _toast.value = it.message ?: "Could not add to playlist" }
        }
    }

    fun createPlaylist(name: String, seedItem: BaseItem?) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            runCatching { repo.createPlaylist(trimmed, listOfNotNull(seedItem?.id)) }
                .onSuccess {
                    _toast.value = if (seedItem == null) {
                        "Created \"$trimmed\""
                    } else {
                        "Created \"$trimmed\" with 1 song"
                    }
                    _playlistRevision.value++
                    refreshPlaylists()
                    dismissSheet()
                }
                .onFailure { _toast.value = it.message ?: "Could not create playlist" }
        }
    }

    fun removeFromPlaylist(context: PlaylistContext) {
        val entryId = context.playlistItemId ?: run {
            _toast.value = "This track cannot be removed"
            return
        }
        scope.launch {
            runCatching { repo.removeFromPlaylist(context.playlistId, listOf(entryId)) }
                .onSuccess {
                    _toast.value = "Removed from playlist"
                    _playlistRevision.value++
                    dismissSheet()
                }
                .onFailure { _toast.value = it.message ?: "Could not remove from playlist" }
        }
    }

    fun deletePlaylist(playlistId: String, onDeleted: () -> Unit) {
        scope.launch {
            runCatching { repo.deletePlaylist(playlistId) }
                .onSuccess {
                    _toast.value = "Playlist deleted"
                    _playlistRevision.value++
                    refreshPlaylists()
                    onDeleted()
                }
                .onFailure { _toast.value = it.message ?: "Could not delete playlist" }
        }
    }

    private fun BaseItem.toTrack() = PlayableTrack(
        id = id,
        title = name.orEmpty(),
        artist = artistName.orEmpty(),
        album = album.orEmpty(),
        streamUrl = repo.streamUrl(id),
        artworkUrl = repo.artworkFor(this)
    )
}
