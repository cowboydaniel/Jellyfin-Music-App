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

enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    SONGS("Songs");

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: PLAYLISTS
    }
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.PLAYLISTS,
    val items: List<BaseItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isGrid: Boolean = true
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state

    private val cache = mutableMapOf<LibraryTab, List<BaseItem>>()

    fun loadOnce() {
        if (cache.isEmpty()) select(_state.value.tab)
    }

    fun select(tab: LibraryTab) {
        cache[tab]?.let {
            _state.value = _state.value.copy(tab = tab, items = it, isLoading = false, error = null)
            return
        }
        _state.value = _state.value.copy(tab = tab, isLoading = true, error = null, items = emptyList())
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    LibraryTab.PLAYLISTS -> repo.playlists()
                    LibraryTab.ALBUMS -> repo.allAlbums()
                    LibraryTab.ARTISTS -> repo.artists()
                    LibraryTab.SONGS -> repo.allSongs()
                }
            }
                .onSuccess {
                    cache[tab] = it
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
}
