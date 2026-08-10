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

/** More from the same artist and the same album, for the player's Related tab. */
data class RelatedState(
    val itemId: String? = null,
    val moreFromArtist: List<com.jellyfinmusic.network.BaseItem> = emptyList(),
    val moreFromAlbum: List<com.jellyfinmusic.network.BaseItem> = emptyList(),
    val artistName: String = "",
    val albumName: String = "",
    val isLoading: Boolean = false
) {
    val isEmpty: Boolean get() = moreFromArtist.isEmpty() && moreFromAlbum.isEmpty()
}

data class LyricsState(
    val itemId: String? = null,
    val lines: List<LyricLine> = emptyList(),
    val isLoading: Boolean = false
) {
    /** True when every line carries a timestamp, so the view can follow along. */
    val isSynced: Boolean get() = lines.isNotEmpty() && lines.all { it.startMs != null }
}

@androidx.media3.common.util.UnstableApi
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

    val sleepTimerEndsAt = player.sleepTimerEndsAt
    val dislikedIds = actions.dislikedIds

    private val _related = MutableStateFlow(RelatedState())
    val related: StateFlow<RelatedState> = _related

    fun toggleDislike(itemId: String) = actions.toggleDislikeById(itemId)

    private fun withCurrentItem(block: (com.jellyfinmusic.network.BaseItem) -> Unit) {
        val itemId = player.state.value.currentItemId ?: return
        viewModelScope.launch {
            runCatching { repo.itemById(itemId) }.getOrNull()?.let(block)
        }
    }

    fun shareCurrent() = withCurrentItem(actions::shareItem)

    fun goToAlbumOfCurrent() = withCurrentItem(actions::goToAlbum)

    fun goToArtistOfCurrent() = withCurrentItem(actions::goToArtist)

    /**
     * Fills the Related tab with more from the same artist and the rest of the
     * album — the two things Jellyfin can answer well, in place of the
     * catalogue-wide recommendations a streaming service would show.
     */
    fun loadRelated(itemId: String?) {
        if (itemId == null || _related.value.itemId == itemId) return
        _related.value = RelatedState(itemId = itemId, isLoading = true)
        viewModelScope.launch {
            val item = runCatching { repo.itemById(itemId) }.getOrNull()
            if (item == null) {
                _related.value = RelatedState(itemId = itemId, isLoading = false)
                return@launch
            }
            val artist = item.artistItems.firstOrNull { !it.id.isNullOrBlank() }
            val fromArtist = artist?.id?.let {
                runCatching { repo.topSongsOfArtist(it, limit = 12) }.getOrDefault(emptyList())
            }.orEmpty().filter { it.id != itemId }
            val fromAlbum = item.albumId?.let {
                runCatching { repo.tracksOfAlbum(it) }.getOrDefault(emptyList())
            }.orEmpty().filter { it.id != itemId }

            if (_related.value.itemId == itemId) {
                _related.value = RelatedState(
                    itemId = itemId,
                    moreFromArtist = fromArtist,
                    moreFromAlbum = fromAlbum,
                    artistName = artist?.name ?: item.artistName.orEmpty(),
                    albumName = item.album.orEmpty(),
                    isLoading = false
                )
            }
        }
    }

    /** Plays a related track next rather than replacing what is queued. */
    fun playRelated(item: com.jellyfinmusic.network.BaseItem) = actions.playNext(item)

    fun artworkFor(item: com.jellyfinmusic.network.BaseItem): String? = repo.artworkFor(item)

    fun toggleFavorite(itemId: String) = actions.toggleFavoriteById(itemId)

    fun setSleepTimer(minutes: Int) = player.setSleepTimer(minutes)

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
