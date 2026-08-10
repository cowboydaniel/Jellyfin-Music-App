package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "",
    val quickPicks: List<BaseItem> = emptyList(),
    val listenAgain: List<BaseItem> = emptyList(),
    val newReleases: List<BaseItem> = emptyList(),
    val playlists: List<BaseItem> = emptyList(),
    val artists: List<BaseItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private var loaded = false

    fun loadOnce() {
        if (loaded) return
        loaded = true
        refresh()
    }

    fun refresh() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    // Each shelf is independent, so they are fetched together and
                    // a failure in one is allowed to leave that shelf empty rather
                    // than blanking the whole screen.
                    val topSongs = async { orEmpty { repo.topSongs(40) } }
                    val listenAgain = async { orEmpty { repo.recentlyPlayedAlbums(20) } }
                    val newReleases = async { orEmpty { repo.latestAlbums(20) } }
                    val playlists = async { orEmpty { repo.playlists() } }
                    val artists = async { orEmpty { repo.topArtists(20) } }

                    var picks = topSongs.await()
                    // A library that has never been played reports no top songs,
                    // so fall back to a random selection to keep Home populated.
                    if (picks.isEmpty()) picks = orEmpty { repo.randomSongs(40) }

                    HomeUiState(
                        greeting = greeting(),
                        quickPicks = picks,
                        listenAgain = listenAgain.await(),
                        newReleases = newReleases.await(),
                        playlists = playlists.await(),
                        artists = artists.await(),
                        isLoading = false
                    )
                }
            }
                .onSuccess { _state.value = it }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = it.message ?: "Could not reach the server"
                    )
                }
        }
    }

    fun playQuickPicks(index: Int) {
        val picks = _state.value.quickPicks
        if (picks.isEmpty()) return
        player.playQueue(picks.toPlayable(repo), index)
    }

    /** Plays an album or playlist straight from a card, without opening it first. */
    fun playContainer(item: BaseItem, shuffle: Boolean = false) {
        viewModelScope.launch {
            val tracks = runCatching {
                if (item.type == "Playlist") repo.playlistTracks(item.id) else repo.tracksOfAlbum(item.id)
            }.getOrDefault(emptyList())
            if (tracks.isEmpty()) return@launch
            val ordered = if (shuffle) tracks.shuffled() else tracks
            player.playQueue(ordered.toPlayable(repo), 0)
        }
    }

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    private suspend fun orEmpty(block: suspend () -> List<BaseItem>): List<BaseItem> =
        runCatching { block() }.getOrDefault(emptyList())

    private fun greeting(): String {
        val name = settings.current.username
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (name.isBlank()) part else "$part, $name"
    }
}
