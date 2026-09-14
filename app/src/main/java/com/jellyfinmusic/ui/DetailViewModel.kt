package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.PlaylistContext
import com.jellyfinmusic.data.toBaseItem
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
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
 * Backs the album, playlist, artist and Liked songs screens: a header plus one
 * or two lists. Which loader the screen calls decides what gets filled in.
 */
@androidx.media3.common.util.UnstableApi
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    private val player: PlayerConnection,
    private val downloads: com.jellyfinmusic.data.DownloadsController,
    val actions: ActionsController
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    val favoriteIds = repo.favoriteIds

    /** Set while showing a playlist, so tracks can be removed from it. */
    private var currentPlaylistId: String? = null

    private var reloadCurrent: (() -> Unit)? = null

    init {
        // Adding or removing a track elsewhere should be reflected here without
        // the user having to back out and return.
        viewModelScope.launch {
            actions.playlistRevision.drop(1).collect { reloadCurrent?.invoke() }
        }
    }

    fun loadAlbum(albumId: String, isPlaylist: Boolean) {
        currentPlaylistId = albumId.takeIf { isPlaylist }
        reloadCurrent = { loadAlbum(albumId, isPlaylist) }
        load {
            runCatching {
                val tracks =
                    if (isPlaylist) repo.playlistTracks(albumId) else repo.tracksOfAlbum(albumId)
                // Falls back to a stub rather than null: the header is what
                // identifies the collection when downloading, so losing it
                // silently turns a playlist download into loose tracks.
                val header = repo.itemById(albumId) ?: BaseItem(
                    id = albumId,
                    name = tracks.firstOrNull()?.album.takeIf { !isPlaylist } ?: "Playlist",
                    type = if (isPlaylist) "Playlist" else "MusicAlbum"
                )
                DetailUiState(header = header, tracks = tracks, isLoading = false)
            }.getOrElse { error ->
                // A downloaded collection stays openable with no server, which
                // is the whole point of having downloaded it.
                offlineState(albumId) ?: throw error
            }
        }
    }

    /**
     * The downloaded copy of a collection, if there is one. Playback runs from
     * the saved tracks rather than the displayed ones, since those carry the
     * artwork and stream key the cache was filled under.
     */
    private fun offlineState(collectionId: String): DetailUiState? {
        val saved = downloads.collectionTracks(collectionId)
        if (saved.isEmpty()) return null
        val collection = downloads.collectionById(collectionId)
        offlineTracks = saved
        return DetailUiState(
            header = collection?.toBaseItem(),
            tracks = saved.map { it.toBaseItem() },
            isLoading = false
        )
    }

    /** Set while showing a collection served from downloads. */
    private var offlineTracks: List<com.jellyfinmusic.data.SavedTrack> = emptyList()

    fun loadArtist(artistId: String) {
        currentPlaylistId = null
        reloadCurrent = { loadArtist(artistId) }
        load {
            val header = repo.itemById(artistId)
            val albums = repo.albumsOfArtist(artistId)
            val tracks = runCatching { repo.topSongsOfArtist(artistId) }
                .getOrDefault(emptyList())
                // Fall back to the first album if the artist query returns
                // nothing, so the page is never actionless.
                .ifEmpty { albums.firstOrNull()?.let { repo.tracksOfAlbum(it.id) }.orEmpty() }
            DetailUiState(header = header, albums = albums, tracks = tracks, isLoading = false)
        }
    }

    /** Everything played recently, newest first. */
    fun loadHistory() {
        currentPlaylistId = null
        reloadCurrent = { loadHistory() }
        load { DetailUiState(tracks = repo.recentlyPlayedSongs(100), isLoading = false) }
    }

    /** The Liked songs view: every track the user has favourited. */
    fun loadLikedSongs() {
        currentPlaylistId = null
        reloadCurrent = { loadLikedSongs() }
        load { DetailUiState(tracks = repo.favoriteSongs(), isLoading = false) }
    }

    fun play(index: Int) {
        val queue = playableQueue()
        if (queue.isEmpty()) return
        player.playQueue(queue, index)
    }

    fun playAll(shuffle: Boolean) {
        val queue = playableQueue()
        if (queue.isEmpty()) return
        player.playQueue(if (shuffle) queue.shuffled() else queue, 0)
    }

    /** Prefers the downloaded copies, so a collection plays with no server. */
    private fun playableQueue() = offlineTracks.takeIf { it.isNotEmpty() }
        ?.map(downloads::toPlayable)
        ?: _state.value.tracks.toPlayable(repo)

    /** Queues the server's instant mix for this item — the "start radio" action. */
    fun startRadio() {
        val seed = _state.value.header?.id ?: _state.value.tracks.firstOrNull()?.id ?: return
        viewModelScope.launch {
            val mix = runCatching { repo.instantMix(seed) }.getOrDefault(emptyList())
            if (mix.isNotEmpty()) player.playQueue(mix.toPlayable(repo), 0)
        }
    }

    fun showMenu(item: BaseItem) {
        val context = currentPlaylistId?.let { PlaylistContext(it, item.playlistItemId) }
        actions.showTrackMenu(item, context)
    }

    fun toggleFavorite(item: BaseItem) = actions.toggleFavorite(item)

    /** Per-track download state, so the header button can show progress. */
    val downloadStates = downloads.states

    fun downloadAll() {
        val header = _state.value.header
        val label = header?.name ?: "these tracks"
        actions.downloadAll(_state.value.tracks, label, header)
    }

    /**
     * Stops a download in progress and clears whatever already landed. Queued
     * tracks cannot be left behind: a half-downloaded collection that cannot be
     * cancelled just keeps spending data.
     */
    fun stopDownload() {
        val header = _state.value.header
        // The recorded collection and the visible tracks can differ -- the list
        // may have been reordered or edited since -- so both are cancelled.
        val ids = _state.value.tracks.map { it.id } +
            header?.id?.let { downloads.collectionById(it)?.trackIds }.orEmpty()
        header?.let { downloads.removeCollection(it.id) }
        downloads.removeMany(ids.toSet())
        actions.notify("Download cancelled")
    }

    fun deleteCurrentPlaylist(onDeleted: () -> Unit) {
        val id = currentPlaylistId ?: return
        actions.deletePlaylist(id, onDeleted)
    }

    val isPlaylist: Boolean get() = currentPlaylistId != null

    // ---- Selection --------------------------------------------------------

    private val _selected = MutableStateFlow<Set<String>?>(null)

    /** Null when not selecting; a set of track IDs once selection starts. */
    val selected: StateFlow<Set<String>?> = _selected

    /** Only playlists support removal, so only they can start a selection. */
    fun startSelection(track: BaseItem) {
        if (currentPlaylistId == null) return
        _selected.value = setOf(track.id)
    }

    fun toggleSelected(track: BaseItem) {
        val current = _selected.value ?: return
        val updated = if (track.id in current) current - track.id else current + track.id
        // Unticking the last row leaves selection mode.
        _selected.value = updated.ifEmpty { null }
    }

    fun selectAll() {
        if (currentPlaylistId == null) return
        _selected.value = _state.value.tracks.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selected.value = null
    }

    /** Removes every ticked track from the playlist being shown. */
    fun removeSelected() {
        val playlistId = currentPlaylistId ?: return
        val ids = _selected.value.orEmpty()
        // Entry IDs, since the same track can appear in a playlist twice.
        val entryIds = _state.value.tracks
            .filter { it.id in ids }
            .mapNotNull { it.playlistItemId }
        _selected.value = null
        actions.removeManyFromPlaylist(playlistId, entryIds)
    }

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)

    private fun load(block: suspend () -> DetailUiState) {
        // Cleared up front so a previous offline collection cannot supply the
        // queue for whatever is loaded next.
        offlineTracks = emptyList()
        _selected.value = null
        _state.value = _state.value.copy(isLoading = true, error = null)
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
