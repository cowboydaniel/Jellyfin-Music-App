package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.PlaylistContext
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailUiState(
    val header: BaseItem? = null,
    val tracks: List<BaseItem> = emptyList(),
    val albums: List<BaseItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * Backs the album, playlist, artist and Liked songs screens: a header plus one
 * or two lists. Which loader the screen calls decides what gets filled in.
 */
@androidx.media3.common.util.UnstableApi
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection,
    val actions: ActionsController
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    val favoriteIds = repo.favoriteIds

    /** Set while showing a playlist, so tracks can be removed from it. */
    private var currentPlaylistId: String? = null

    private var reloadCurrent: (() -> Unit)? = null

    init {
        // Adding or removing a track elsewhere should be reflected here without
        // the user having to back out and return.
        viewModelScope.launch {
            actions.playlistRevision.drop(1).collect { reloadCurrent?.invoke() }
        }
    }

    fun loadAlbum(albumId: String, isPlaylist: Boolean) {
        currentPlaylistId = albumId.takeIf { isPlaylist }
        reloadCurrent = { loadAlbum(albumId, isPlaylist) }
        load {
            val header = repo.itemById(albumId)
            val tracks = if (isPlaylist) repo.playlistTracks(albumId) else repo.tracksOfAlbum(albumId)
            DetailUiState(header = header, tracks = tracks, isLoading = false)
        }
    }

    fun loadArtist(artistId: String) {
        currentPlaylistId = null
        reloadCurrent = { loadArtist(artistId) }
        load {
            val header = repo.itemById(artistId)
            val albums = repo.albumsOfArtist(artistId)
            val tracks = runCatching { repo.topSongsOfArtist(artistId) }
                .getOrDefault(emptyList())
                // Fall back to the first album if the artist query returns
                // nothing, so the page is never actionless.
                .ifEmpty { albums.firstOrNull()?.let { repo.tracksOfAlbum(it.id) }.orEmpty() }
            DetailUiState(header = header, albums = albums, tracks = tracks, isLoading = false)
        }
    }

    /** The Liked songs view: every track the user has favourited. */
    fun loadLikedSongs() {
        currentPlaylistId = null
        reloadCurrent = { loadLikedSongs() }
        load { DetailUiState(tracks = repo.favoriteSongs(), isLoading = false) }
    }

    fun play(index: Int) {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        player.playQueue(tracks.toPlayable(repo), index)
    }

    fun playAll(shuffle: Boolean) {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val ordered = if (shuffle) tracks.shuffled() else tracks
        player.playQueue(ordered.toPlayable(repo), 0)
    }

    /** Queues the server's instant mix for this item — the "start radio" action. */
    fun startRadio() {
        val seed = _state.value.header?.id ?: _state.value.tracks.firstOrNull()?.id ?: return
        viewModelScope.launch {
            val mix = runCatching { repo.instantMix(seed) }.getOrDefault(emptyList())
            if (mix.isNotEmpty()) player.playQueue(mix.toPlayable(repo), 0)
        }
    }

    fun showMenu(item: BaseItem) {
        val context = currentPlaylistId?.let { PlaylistContext(it, item.playlistItemId) }
        actions.showTrackMenu(item, context)
    }

    fun toggleFavorite(item: BaseItem) = actions.toggleFavorite(item)

    fun downloadAll() {
        val label = _state.value.header?.name ?: "these tracks"
        actions.downloadAll(_state.value.tracks, label)
    }

    fun deleteCurrentPlaylist(onDeleted: () -> Unit) {
        val id = currentPlaylistId ?: return
        actions.deletePlaylist(id, onDeleted)
    }

    val isPlaylist: Boolean get() = currentPlaylistId != null

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    private fun load(block: suspend () -> DetailUiState) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _state.value = it }
                .onFailure {
                    _state.value = DetailUiState(
                        isLoading = false,
                        error = it.message ?: "Could not reach the server"
                    )
                }
        }
    }
}
