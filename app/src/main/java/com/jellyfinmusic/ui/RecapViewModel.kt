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

data class RecapUiState(
    val totalPlays: Int = 0,
    val distinctTracks: Int = 0,
    val minutes: Long = 0,
    val topSongs: List<BaseItem> = emptyList(),
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topAlbums: List<Pair<String, Int>> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * A listening summary built from Jellyfin's play counts.
 *
 * There is no history API to page through, so everything here is derived from
 * the per-track counts the server keeps — which means it is a lifetime summary
 * rather than a calendar year, and it only counts what clients have reported.
 */
@HiltViewModel
class RecapViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection
) : ViewModel() {

    private val _state = MutableStateFlow(RecapUiState())
    val state: StateFlow<RecapUiState> = _state

    private var loaded = false

    fun loadOnce() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            runCatching { repo.topSongs(limit = 200) }
                .onSuccess { songs ->
                    val played = songs.filter { (it.userData?.playCount ?: 0) > 0 }
                    _state.value = RecapUiState(
                        totalPlays = played.sumOf { it.userData?.playCount ?: 0 },
                        distinctTracks = played.size,
                        minutes = played.sumOf {
                            (it.durationMs / 60_000L) * (it.userData?.playCount ?: 0)
                        },
                        topSongs = played.take(10),
                        topArtists = played.tally { it.artistName },
                        topAlbums = played.tally { it.album },
                        isLoading = false
                    )
                }
                .onFailure {
                    _state.value = RecapUiState(
                        isLoading = false,
                        error = it.message ?: "Could not reach the server"
                    )
                }
        }
    }

    fun playTopSong(index: Int) {
        val songs = _state.value.topSongs
        if (songs.isEmpty()) return
        player.playQueue(songs.toPlayable(repo), index)
    }

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    /** Sums play counts by whatever key [selector] picks out. */
    private fun List<BaseItem>.tally(selector: (BaseItem) -> String?): List<Pair<String, Int>> =
        groupBy { selector(it).orEmpty() }
            .filterKeys { it.isNotBlank() }
            .map { (name, items) -> name to items.sumOf { it.userData?.playCount ?: 0 } }
            .sortedByDescending { it.second }
            .take(5)
}
