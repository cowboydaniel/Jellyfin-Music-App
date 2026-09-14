package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.toBaseItem
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
    val sort: LibrarySort = LibrarySort.A_TO_Z,
    /** Null when not selecting; a set (possibly empty) once selection starts. */
    val selected: Set<String>? = null
) {
    val inSelectionMode: Boolean get() = selected != null

    /**
     * Only downloads and playlists can be deleted from here. Deleting an album,
     * artist or song would delete the media from the server, which is not what
     * anyone means by long-pressing a row in their library.
     */
    val canSelect: Boolean
        get() = tab == LibraryTab.DOWNLOADS || tab == LibraryTab.PLAYLISTS
}

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

        // Downloads arrive in the background, long after the tab was opened, so
        // the list follows the download state instead of sampling it once.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                downloads.downloadedCollections,
                downloads.downloadedTracks
            ) { collections, tracks ->
                downloadItems(collections, tracks)
            }.collect { items ->
                if (_state.value.tab == LibraryTab.DOWNLOADS) {
                    _state.value = _state.value.copy(items = items, isLoading = false, error = null)
                }
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
        // A selection belongs to the list it was made in.
        if (tab != _state.value.tab) _state.value = _state.value.copy(selected = null)
        cache[tab to sort]?.takeIf { tab != LibraryTab.DOWNLOADS }?.let {
            _state.value = _state.value.copy(tab = tab, items = it, isLoading = false, error = null)
            return
        }
        _state.value = _state.value.copy(tab = tab, isLoading = true, error = null, items = emptyList())
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    // Downloads are read from the local cache so the tab works
                    // with no server connection at all.
                    // Downloaded playlists and albums lead, so they can be
                    // opened as collections rather than hunted for among the
                    // loose tracks they brought down.
                    // Only kicks the refresh; the collector above delivers the
                    // result, here and again as later downloads land.
                    LibraryTab.DOWNLOADS -> {
                        downloads.refresh()
                        downloadItems(
                            downloads.downloadedCollections.value,
                            downloads.downloadedTracks.value
                        )
                    }
                    LibraryTab.PLAYLISTS -> repo.playlists(sort.key, sort.order)
                    LibraryTab.ALBUMS -> repo.allAlbums(sortBy = sort.key, sortOrder = sort.order)
                    // Artists carry no played or added date of their own.
                    LibraryTab.ARTISTS -> repo.artists()
                    LibraryTab.SONGS -> repo.allSongs(sortBy = sort.key, sortOrder = sort.order)
                }
            }
                .onSuccess {
                    // Downloads change under the tab, so caching it would
                    // freeze whatever happened to be on disk at first open.
                    if (tab != LibraryTab.DOWNLOADS) cache[tab to sort] = it
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
        val items = _state.value.items
        val tapped = items.getOrNull(index) ?: return
        // The Downloads tab lists collections above the loose tracks, so the
        // row index is not the queue index and the collections themselves must
        // not end up in the queue.
        val songs = items.filter { it.type != "Playlist" && it.type != "MusicAlbum" }
        val start = songs.indexOfFirst { it.id == tapped.id }
        if (start < 0) return
        player.playQueue(songs.toPlayable(repo), start)
    }

    /**
     * The Downloads tab: collections first, then only the tracks that were
     * downloaded on their own. A track that came down as part of a playlist is
     * reachable by opening that playlist, so listing it loose as well just
     * buries the collections under their own contents.
     */
    private fun downloadItems(
        collections: List<com.jellyfinmusic.data.DownloadedCollection>,
        tracks: List<com.jellyfinmusic.data.SavedTrack>
    ): List<BaseItem> {
        val inCollections = collections.flatMap { it.trackIds }.toSet()
        return collections.map { it.toBaseItem() } +
            tracks.filterNot { it.id in inCollections }.map { it.toBaseItem() }
    }

    // ---- Selection --------------------------------------------------------

    /** Long-press starts selection with that row already ticked. */
    fun startSelection(item: BaseItem) {
        if (!_state.value.canSelect) return
        _state.value = _state.value.copy(selected = setOf(item.id))
    }

    fun toggleSelected(item: BaseItem) {
        val current = _state.value.selected ?: return
        val updated = if (item.id in current) current - item.id else current + item.id
        // Unticking the last row leaves selection mode, so there is always a
        // way back out without hunting for the close button.
        _state.value = _state.value.copy(selected = updated.ifEmpty { null })
    }

    fun selectAll() {
        if (!_state.value.canSelect) return
        _state.value = _state.value.copy(selected = _state.value.items.map { it.id }.toSet())
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = null)
    }

    /**
     * Deletes everything ticked: downloads come off the device, playlists are
     * deleted on the server. Selection ends either way.
     */
    fun deleteSelected() {
        val ids = _state.value.selected.orEmpty()
        if (ids.isEmpty()) return
        val items = _state.value.items.filter { it.id in ids }
        when (_state.value.tab) {
            LibraryTab.DOWNLOADS -> items.forEach {
                when (it.type) {
                    "Playlist", "MusicAlbum" -> downloads.removeCollection(it.id)
                    else -> downloads.remove(it.id)
                }
            }
            LibraryTab.PLAYLISTS -> items.forEach { actions.deletePlaylist(it.id) {} }
            else -> Unit
        }
        _state.value = _state.value.copy(selected = null)
    }

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    /** Plays everything in the current tab in random order. */
    fun shuffleAll() {
        val items = _state.value.items
        if (items.isEmpty()) return
        if (_state.value.tab == LibraryTab.DOWNLOADS) {
            // Everything on disk, including tracks inside collections -- those
            // are hidden from the list, but "shuffle all" still means all.
            val songs = downloads.downloadedTracks.value
            if (songs.isEmpty()) return
            player.playQueue(songs.shuffled().map(downloads::toPlayable), 0)
        } else if (_state.value.tab == LibraryTab.SONGS) {
            player.playQueue(items.shuffled().toPlayable(repo), 0)
        }
    }

}
