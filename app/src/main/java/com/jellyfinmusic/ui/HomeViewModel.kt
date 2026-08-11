package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "",
    /** The 3x3 paged grid at the top: recent albums, artists and playlists. */
    val speedDial: List<BaseItem> = emptyList(),
    val moods: List<BaseItem> = emptyList(),
    val continueListening: List<BaseItem> = emptyList(),
    val topSongs: List<BaseItem> = emptyList(),
    val quickPicks: List<BaseItem> = emptyList(),
    val listenAgain: List<BaseItem> = emptyList(),
    val newReleases: List<BaseItem> = emptyList(),
    val playlists: List<BaseItem> = emptyList(),
    val artists: List<BaseItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection,
    private val settings: SettingsStore,
    private val actions: ActionsController,
    private val downloads: com.jellyfinmusic.data.DownloadsController,
    private val dismissed: com.jellyfinmusic.data.DismissedStore
) : ViewModel() {

    val favoriteIds = repo.favoriteIds

    fun showMenu(item: BaseItem) = actions.showTrackMenu(item)

    val dismissedIds = dismissed.ids

    fun dismiss(item: BaseItem) {
        dismissed.dismiss(item.id)
        actions.notify("Hidden from Home")
    }

    /** Radio built from a mood: a shuffled run through one genre. */
    fun startGenreRadio(genre: BaseItem) {
        val name = genre.name ?: return
        viewModelScope.launch {
            val tracks = runCatching { repo.tracksInGenre(name) }.getOrDefault(emptyList())
            if (tracks.isEmpty()) {
                actions.notify("Nothing in ${'$'}name yet")
                return@launch
            }
            player.playQueue(tracks.toPlayable(repo), 0)
            actions.notify("Playing ${'$'}name")
        }
    }

    fun toggleFavorite(item: BaseItem) = actions.toggleFavorite(item)

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    private var loaded = false

    init {
        // Home shows playlists too, so it reloads when the set changes.
        viewModelScope.launch {
            actions.playlistRevision.drop(1).collect { refresh() }
        }
    }

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
                    val topSongsAsync = async { orEmpty { repo.topSongs(40) } }
                    val listenAgain = async { orEmpty { repo.recentlyPlayedAlbums(20) } }
                    val newReleases = async { orEmpty { repo.latestAlbums(20) } }
                    val playlists = async { orEmpty { repo.playlists() } }
                    val artists = async { orEmpty { repo.topArtists(20) } }
                    val moods = async { orEmpty { repo.genres() } }
                    val resumable = async { orEmpty { repo.partiallyPlayedAlbums(12) } }

                    var picks = topSongsAsync.await()
                    // A library that has never been played reports no top songs,
                    // so fall back to a random selection to keep Home populated.
                    if (picks.isEmpty()) picks = orEmpty { repo.randomSongs(40) }

                    val recentAlbums = listenAgain.await()
                    val allPlaylists = playlists.await()
                    val allArtists = artists.await()

                    // Speed dial mixes the shapes the way YouTube Music does:
                    // whatever you have been near lately, regardless of type.
                    val dial = (recentAlbums + allPlaylists.take(6) + allArtists.take(6))
                        .distinctBy { it.id }
                        .take(SPEED_DIAL_SIZE)

                    HomeUiState(
                        greeting = greeting(),
                        speedDial = dial,
                        moods = moods.await().take(12),
                        continueListening = resumable.await(),
                        topSongs = picks.take(20),
                        quickPicks = picks,
                        listenAgain = recentAlbums,
                        newReleases = newReleases.await(),
                        playlists = allPlaylists,
                        artists = allArtists,
                        isLoading = false
                    )
                }
            }
                .onSuccess {
                    _state.value = it
                    // Home is the natural place to run this: it loads on
                    // launch, and the list it needs is a query away.
                    syncSmartDownloads()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = it.message ?: "Could not reach the server"
                    )
                }
        }
    }

    private fun syncSmartDownloads() {
        viewModelScope.launch {
            val recent = runCatching { repo.recentlyPlayedSongs(50) }.getOrDefault(emptyList())
            if (recent.isNotEmpty()) downloads.syncSmartDownloads(recent)
        }
    }

    fun playTopSong(index: Int) {
        val songs = _state.value.topSongs
        if (songs.isEmpty()) return
        player.playQueue(songs.toPlayable(repo), index)
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

    private companion object {
        const val SPEED_DIAL_SIZE = 27
    }

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
