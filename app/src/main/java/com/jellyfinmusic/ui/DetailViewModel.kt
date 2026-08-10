package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
 * Backs both the album/playlist screen (header + track list) and the artist
 * screen (header + top tracks + albums); which lists are filled depends on
 * which loader the screen calls.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    fun loadAlbum(albumId: String, isPlaylist: Boolean) {
        load {
            val header = repo.itemById(albumId)
            val tracks = if (isPlaylist) repo.playlistTracks(albumId) else repo.tracksOfAlbum(albumId)
            DetailUiState(header = header, tracks = tracks, isLoading = false)
        }
    }

    fun loadArtist(artistId: String) {
        load {
            val header = repo.itemById(artistId)
            val albums = repo.albumsOfArtist(artistId)
            // Jellyfin has no "artist top tracks" endpoint, so the first album's
            // tracks stand in as something immediately playable.
            val tracks = albums.firstOrNull()?.let { repo.tracksOfAlbum(it.id) }.orEmpty()
            DetailUiState(header = header, albums = albums, tracks = tracks, isLoading = false)
        }
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

    fun addToQueue(item: BaseItem) = player.addToQueue(item.toPlayable(repo))

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    private fun load(block: suspend () -> DetailUiState) {
        _state.value = DetailUiState(isLoading = true)
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
