package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayableTrack
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListUiState(
    val items: List<BaseItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection
) : ViewModel() {

    private val _artists = MutableStateFlow(ListUiState())
    val artists: StateFlow<ListUiState> = _artists

    private val _albums = MutableStateFlow(ListUiState())
    val albums: StateFlow<ListUiState> = _albums

    private val _tracks = MutableStateFlow(ListUiState())
    val tracks: StateFlow<ListUiState> = _tracks

    private val _playlists = MutableStateFlow(ListUiState())
    val playlists: StateFlow<ListUiState> = _playlists

    fun loadArtists() = load(_artists) { repo.artists() }

    fun loadAlbums(artistId: String) = load(_albums) { repo.albumsOfArtist(artistId) }

    fun loadTracks(albumId: String) = load(_tracks) { repo.tracksOfAlbum(albumId) }

    fun loadPlaylists() = load(_playlists) { repo.playlists() }

    fun loadPlaylistTracks(playlistId: String) = load(_tracks) { repo.playlistTracks(playlistId) }

    /** Starts playback of the currently loaded track list at [index]. */
    fun playTracks(items: List<BaseItem>, index: Int) {
        player.playQueue(items.map { it.toPlayable() }, index)
    }

    fun playShuffled(items: List<BaseItem>) {
        if (items.isEmpty()) return
        player.playQueue(items.shuffled().map { it.toPlayable() }, 0)
    }

    fun addToQueue(item: BaseItem) {
        player.addToQueue(item.toPlayable())
    }

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    private fun BaseItem.toPlayable() = PlayableTrack(
        id = id,
        title = name.orEmpty(),
        artist = artistName.orEmpty(),
        album = album.orEmpty(),
        streamUrl = repo.streamUrl(id),
        artworkUrl = repo.artworkFor(this)
    )

    private fun load(target: MutableStateFlow<ListUiState>, block: suspend () -> List<BaseItem>) {
        target.value = target.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { target.value = ListUiState(items = it) }
                .onFailure {
                    target.value = ListUiState(error = it.message ?: "Could not reach the server")
                }
        }
    }
}
