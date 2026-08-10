package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.LidarrRepository
import com.jellyfinmusic.data.SongMatch
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.network.LidarrAlbum
import com.jellyfinmusic.network.LidarrArtist
import com.jellyfinmusic.network.LidarrImage
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter(val label: String) {
    ALL("All"),
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    REQUEST("Get more");

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: ALL
    }
}

/** Where a request result stands relative to the Jellyfin library. */
enum class LibraryStatus { IN_LIBRARY, NOT_IN_LIBRARY, REQUESTED }

data class RequestResult(
    val key: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val status: LibraryStatus,
    val artist: LidarrArtist? = null,
    val album: LidarrAlbum? = null,
    /**
     * Set for song results. Lidarr cannot grab a single track, so requesting
     * one adds the album it appears on; the UI says so on the row.
     */
    val song: SongMatch? = null
)

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val submitted: Boolean = false,
    val songs: List<BaseItem> = emptyList(),
    val albums: List<BaseItem> = emptyList(),
    val artists: List<BaseItem> = emptyList(),
    val requestResults: List<RequestResult> = emptyList(),
    val isLoading: Boolean = false,
    val isRequestLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val lidarrConfigured: Boolean = false
) {
    val hasLibraryResults: Boolean
        get() = songs.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty()

    /** The single best library match, shown as the "Top result" card. */
    val topResult: BaseItem?
        get() = artists.firstOrNull { it.name.equals(query.trim(), ignoreCase = true) }
            ?: albums.firstOrNull { it.name.equals(query.trim(), ignoreCase = true) }
            ?: songs.firstOrNull { it.name.equals(query.trim(), ignoreCase = true) }
            ?: artists.firstOrNull()
            ?: albums.firstOrNull()
            ?: songs.firstOrNull()
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val jellyfin: JellyfinRepository,
    private val lidarr: LidarrRepository,
    private val player: PlayerConnection,
    private val settings: SettingsStore,
    private val actions: ActionsController
) : ViewModel() {

    val favoriteIds = jellyfin.favoriteIds

    fun showMenu(item: BaseItem) = actions.showTrackMenu(item)

    fun toggleFavorite(item: BaseItem) = actions.toggleFavorite(item)

    private val _state = MutableStateFlow(SearchUiState(lidarrConfigured = settings.current.hasLidarr))
    val state: StateFlow<SearchUiState> = _state

    private val requested = mutableSetOf<String>()

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value, submitted = false)
    }

    fun clearQuery() {
        _state.value = SearchUiState(lidarrConfigured = settings.current.hasLidarr)
    }

    fun onFilterChange(filter: SearchFilter) {
        _state.value = _state.value.copy(filter = filter)
        if (_state.value.query.isNotBlank()) {
            if (filter == SearchFilter.REQUEST) searchLidarr() else search()
        }
    }

    fun refreshConfigFlag() {
        _state.value = _state.value.copy(lidarrConfigured = settings.current.hasLidarr)
    }

    /** Searches the Jellyfin library, and Lidarr too so "Get more" is ready. */
    fun search() {
        val term = _state.value.query.trim()
        if (term.isBlank()) return
        _state.value = _state.value.copy(isLoading = true, error = null, submitted = true)

        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val songs = async { safe { jellyfin.search(term, "Audio", 30) } }
                    val albums = async { safe { jellyfin.search(term, "MusicAlbum", 20) } }
                    val artists = async { safe { jellyfin.search(term, "MusicArtist", 20) } }
                    Triple(songs.await(), albums.await(), artists.await())
                }
            }
                .onSuccess { (songs, albums, artists) ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        songs = songs,
                        albums = albums,
                        artists = artists
                    )
                    searchLidarr()
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = it.message ?: "Search failed"
                    )
                }
        }
    }

    /**
     * Queries Lidarr and flags each result against the Jellyfin library, so the
     * "Get more" tab shows what you already own alongside what you can request.
     */
    fun searchLidarr() {
        val term = _state.value.query.trim()
        if (term.isBlank()) return
        if (!settings.current.hasLidarr) {
            _state.value = _state.value.copy(lidarrConfigured = false)
            return
        }
        _state.value = _state.value.copy(isRequestLoading = true, lidarrConfigured = true)

        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val artistsD = async { runCatching { lidarr.lookupArtists(term) }.getOrDefault(emptyList()) }
                    val albumsD = async { runCatching { lidarr.lookupAlbums(term) }.getOrDefault(emptyList()) }
                    val ownedD = async {
                        (safe { jellyfin.search(term, "MusicArtist", 50) } +
                            safe { jellyfin.search(term, "MusicAlbum", 50) })
                            .mapNotNull { it.name?.normalizeForMatch() }
                            .toSet()
                    }
                    val songsD = async {
                        runCatching { lidarr.lookupSongs(term) }.getOrDefault(emptyList())
                    }
                    // Songs are matched against the library by title, so a track
                    // you already have is not offered as a request.
                    val ownedSongsD = async {
                        safe { jellyfin.search(term, "Audio", 50) }
                            .mapNotNull { it.name?.normalizeForMatch() }
                            .toSet()
                    }

                    val owned = ownedD.await()
                    val ownedSongs = ownedSongsD.await()

                    val songResults = songsD.await().map { song ->
                        val status = when {
                            requested.contains(song.key()) -> LibraryStatus.REQUESTED
                            ownedSongs.contains(song.title.normalizeForMatch()) -> LibraryStatus.IN_LIBRARY
                            else -> LibraryStatus.NOT_IN_LIBRARY
                        }
                        song.toResult(status)
                    }

                    val results = artistsD.await().map { it.toResult() } +
                        albumsD.await().map { it.toResult() }

                    val flagged = results.map { result ->
                        val status = when {
                            requested.contains(result.key) -> LibraryStatus.REQUESTED
                            owned.contains(result.title.normalizeForMatch()) -> LibraryStatus.IN_LIBRARY
                            else -> LibraryStatus.NOT_IN_LIBRARY
                        }
                        result.copy(status = status)
                    }

                    (flagged + songResults).sortedBy { it.status.ordinal }
                }
            }
                .onSuccess {
                    _state.value = _state.value.copy(isRequestLoading = false, requestResults = it)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isRequestLoading = false,
                        error = it.message ?: "Lidarr search failed"
                    )
                }
        }
    }

    fun playSong(index: Int) {
        val songs = _state.value.songs
        if (songs.isEmpty()) return
        player.playQueue(songs.toPlayable(jellyfin), index)
    }

    /** Sends a not-in-library result to Lidarr, monitored and with search-on-add. */
    fun request(result: RequestResult) {
        if (result.status != LibraryStatus.NOT_IN_LIBRARY) return
        _state.value = _state.value.copy(isRequestLoading = true, error = null, message = null)

        viewModelScope.launch {
            val outcome = when {
                result.artist != null -> lidarr.addArtist(result.artist).map { }
                result.album != null -> lidarr.addAlbum(result.album).map { }
                result.song != null -> requestSong(result.song)
                else -> Result.failure(IllegalStateException("Nothing to request"))
            }
            val confirmation = if (result.song != null) {
                "Requested \"${result.song.albumTitle}\" for \"${result.title}\" — " +
                    "Lidarr grabs whole albums"
            } else {
                "Requested \"${result.title}\" — Lidarr is searching for it"
            }
            outcome
                .onSuccess {
                    requested.add(result.key)
                    _state.value = _state.value.copy(
                        isRequestLoading = false,
                        message = confirmation,
                        requestResults = _state.value.requestResults.map {
                            if (it.key == result.key) it.copy(status = LibraryStatus.REQUESTED) else it
                        }
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isRequestLoading = false,
                        error = it.message ?: "Could not add to Lidarr"
                    )
                }
        }
    }

    /**
     * Turns a song into a request by resolving the album that carries it and
     * adding that, since Lidarr has no smaller unit to grab.
     */
    private suspend fun requestSong(song: SongMatch): Result<Unit> {
        val album = runCatching { lidarr.resolveAlbumForSong(song) }.getOrNull()
            ?: return Result.failure(
                IllegalStateException("Lidarr could not find the album \"${song.albumTitle}\"")
            )
        return lidarr.addAlbum(album).map { }
    }

    fun imageUrl(item: BaseItem): String? = jellyfin.artworkFor(item)

    private suspend fun safe(block: suspend () -> List<BaseItem>): List<BaseItem> =
        runCatching { block() }.getOrDefault(emptyList())

    private fun LidarrArtist.toResult() = RequestResult(
        key = "artist:" + (foreignArtistId ?: artistName),
        title = artistName,
        subtitle = listOfNotNull("Artist", disambiguation).joinToString(" · "),
        imageUrl = images.firstOrNull { it.coverType == "poster" }?.best()
            ?: images.firstOrNull()?.best(),
        status = LibraryStatus.NOT_IN_LIBRARY,
        artist = this
    )

    private fun LidarrAlbum.toResult() = RequestResult(
        key = "album:" + (foreignAlbumId ?: title),
        title = title,
        subtitle = listOfNotNull("Album", artist?.artistName, releaseDate?.take(4))
            .joinToString(" · "),
        imageUrl = images.firstOrNull { it.coverType == "cover" }?.best()
            ?: images.firstOrNull()?.best(),
        status = LibraryStatus.NOT_IN_LIBRARY,
        album = this
    )

    private fun LidarrImage.best(): String? = remoteUrl ?: url

    private fun SongMatch.key() = "song:$recordingId"

    private fun SongMatch.toResult(status: LibraryStatus) = RequestResult(
        key = key(),
        title = title,
        subtitle = listOfNotNull("Song", artistName, "from $albumTitle").joinToString(" · "),
        imageUrl = artworkUrl,
        status = status,
        song = this
    )
}

/**
 * Jellyfin and Lidarr rarely agree on punctuation or case for the same release,
 * so names are compared with both stripped out.
 */
private fun String.normalizeForMatch(): String = lowercase().filter { it.isLetterOrDigit() }
