package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    DOWNLOADS("Downloads"),
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    SONGS("Songs");

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: PLAYLISTS
    }
}

/** Ordering offered in the library, mapped to Jellyfin sort keys. */
enum class LibrarySort(val label: String, val key: String, val order: String) {
    RECENT_ACTIVITY("Recent activity", "DatePlayed,SortName", "Descending"),
    RECENTLY_ADDED("Recently added", "DateCreated", "Descending"),
    A_TO_Z("A–Z", "SortName", "Ascending");

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: A_TO_Z
    }
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.PLAYLISTS,
    val items: List<BaseItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isGrid: Boolean = true,
    val sort: LibrarySort = LibrarySort.A_TO_Z
)

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection,
    val actions: ActionsController,
    private val downloads: com.jellyfinmusic.data.DownloadsController
) : ViewModel() {

    val favoriteIds = repo.favoriteIds

    private val _state = MutableStateFlow(LibraryUiState(tab = LibraryTab.PLAYLISTS))
    val state: StateFlow<LibraryUiState> = _state

    // Keyed by tab and sort, since changing the order means a different query.
    private val cache = mutableMapOf<Pair<LibraryTab, LibrarySort>, List<BaseItem>>()

    init {
        // Creating, deleting or editing a playlist anywhere invalidates the
        // Playlists tab, so it is reloaded rather than left stale.
        viewModelScope.launch {
            actions.playlistRevision.drop(1).collect {
                cache.keys.filter { key -> key.first == LibraryTab.PLAYLISTS }
                    .forEach(cache::remove)
                if (_state.value.tab == LibraryTab.PLAYLISTS) select(LibraryTab.PLAYLISTS)
            }
        }
    }

    fun loadOnce() {
        if (cache.isEmpty()) select(_state.value.tab)
    }

    /** Playlists get their own menu, since only they can be renamed or re-covered. */
    fun showPlaylistMenu(item: BaseItem) = actions.showPlaylistMenu(item)

    fun showMenu(item: com.jellyfinmusic.network.BaseItem) = actions.showTrackMenu(item)

    fun toggleFavorite(item: com.jellyfinmusic.network.BaseItem) = actions.toggleFavorite(item)

    fun newPlaylist() = actions.showCreatePlaylist(null)

    fun setSort(sort: LibrarySort) {
        if (sort == _state.value.sort) return
        _state.value = _state.value.copy(sort = sort)
        select(_state.value.tab)
    }

    fun select(tab: LibraryTab) {
        val sort = _state.value.sort
        cache[tab to sort]?.let {
            _state.value = _state.value.copy(tab = tab, items = it, isLoading = false, error = null)
            return
        }
        _state.value = _state.value.copy(tab = tab, isLoading = true, error = null, items = emptyList())
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    // Downloads are read from the local cache so the tab works
                    // with no server connection at all.
                    LibraryTab.DOWNLOADS -> {
                        downloads.refresh()
                        downloads.downloadedTracks.value.map { it.toBaseItem() }
                    }
                    LibraryTab.PLAYLISTS -> repo.playlists(sort.key, sort.order)
                    LibraryTab.ALBUMS -> repo.allAlbums(sortBy = sort.key, sortOrder = sort.order)
                    // Artists carry no played or added date of their own.
                    LibraryTab.ARTISTS -> repo.artists()
                    LibraryTab.SONGS -> repo.allSongs(sortBy = sort.key, sortOrder = sort.order)
                }
            }
                .onSuccess {
                    cache[tab to sort] = it
                    if (_state.value.tab == tab) {
                        _state.value = _state.value.copy(items = it, isLoading = false)
                    }
                }
                .onFailure {
                    if (_state.value.tab == tab) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = it.message ?: "Could not reach the server"
                        )
                    }
                }
        }
    }

    fun toggleLayout() {
        _state.value = _state.value.copy(isGrid = !_state.value.isGrid)
    }

    fun refresh() {
        cache.clear()
        select(_state.value.tab)
    }

    /** Plays the Songs tab as a queue starting from the tapped row. */
    fun playSongs(index: Int) {
        val songs = _state.value.items
        if (songs.isEmpty()) return
        player.playQueue(songs.toPlayable(repo), index)
    }

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    /** Plays everything in the current tab in random order. */
    fun shuffleAll() {
        val items = _state.value.items
        if (items.isEmpty()) return
        if (_state.value.tab == LibraryTab.SONGS || _state.value.tab == LibraryTab.DOWNLOADS) {
            player.playQueue(items.shuffled().toPlayable(repo), 0)
        }
    }

    private fun com.jellyfinmusic.data.SavedTrack.toBaseItem() = BaseItem(
        id = id,
        name = title,
        type = "Audio",
        albumArtist = artist,
        album = album
    )
}
