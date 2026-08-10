package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.LyricLine
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsState(
    val itemId: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val isLoading: Boolean = false
) {
    /** True when every line carries a timestamp, so the view can follow along. */
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.startMs != null }
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: PlayerConnection,
    private val actions: ActionsController,
    private val repo: JellyfinRepository
) : ViewModel() {

    val state = player.state
    val favoriteIds = actions.favoriteIds

    private val _lyrics = MutableStateFlow(LyricsState())
    val lyrics: StateFlow<LyricsState> = _lyrics

    fun toggleFavorite(itemId: String) = actions.toggleFavoriteById(itemId)

    /** Opens the shared track menu for whatever is playing. */
    fun showMenuForCurrent() {
        val itemId = player.state.value.currentItemId ?: return
        viewModelScope.launch {
            runCatching { repo.itemById(itemId) }.getOrNull()?.let(actions::showTrackMenu)
        }
    }

    fun addCurrentToPlaylist() {
        val itemId = player.state.value.currentItemId ?: return
        viewModelScope.launch {
            runCatching { repo.itemById(itemId) }.getOrNull()?.let(actions::showAddToPlaylist)
        }
    }

    /** Starts a radio queue seeded from the current track. */
    fun startRadioFromCurrent() {
        val itemId = player.state.value.currentItemId ?: return
        viewModelScope.launch {
            val mix = runCatching { repo.instantMix(itemId) }.getOrDefault(emptyList())
            if (mix.isNotEmpty()) player.playQueue(mix.toPlayable(repo), 0)
        }
    }

    /** Fetched lazily, and only once per track. */
    fun loadLyrics(itemId: String?) {
        if (itemId == null || _lyrics.value.itemId == itemId) return
        _lyrics.value = LyricsState(itemId = itemId, isLoading = true)
        viewModelScope.launch {
            val lines = repo.lyrics(itemId)
            if (_lyrics.value.itemId == itemId) {
                _lyrics.value = LyricsState(itemId = itemId, lines = lines, isLoading = false)
            }
        }
    }
}
