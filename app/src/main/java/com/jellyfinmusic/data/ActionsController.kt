package com.jellyfinmusic.data

import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayableTrack
import com.jellyfinmusic.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
@Singleton
class ActionsController @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection
) {
    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)

    private val _sheet = MutableStateFlow<ActionSheet>(ActionSheet.None)
    val sheet: StateFlow<ActionSheet> = _sheet.asStateFlow()

    private val _playlists = MutableStateFlow<List<BaseItem>>(emptyList())
    val playlists: StateFlow<List<BaseItem>> = _playlists.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** Bumped whenever a playlist changes, so open screens know to reload. */
    private val _playlistRevision = MutableStateFlow(0)
    val playlistRevision: StateFlow<Int> = _playlistRevision.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> get() = repo.favoriteIds

    fun showTrackMenu(item: BaseItem, playlistContext: PlaylistContext? = null) {
        _sheet.value = ActionSheet.TrackMenu(item, playlistContext)
    }

    fun showAddToPlaylist(item: BaseItem) {
        _sheet.value = ActionSheet.AddToPlaylist(item)
        refreshPlaylists()
    }

    fun showCreatePlaylist(seedItem: BaseItem?) {
        _sheet.value = ActionSheet.CreatePlaylist(seedItem)
    }

    fun dismissSheet() {
        _sheet.value = ActionSheet.None
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun clearUserState() {
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
