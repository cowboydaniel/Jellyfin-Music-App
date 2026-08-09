package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.LidarrRepository
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.network.LidarrAlbum
import com.jellyfinmusic.network.LidarrArtist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode { ARTISTS, ALBUMS }

/** Where a search result stands relative to the Jellyfin library. */
enum class LibraryStatus { IN_LIBRARY, NOT_IN_LIBRARY, REQUESTED }

data class SearchResult(
    val key: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val status: LibraryStatus,
    /** Set for artist results; the payload sent back to Lidarr on request. */
    val artist: LidarrArtist? = null,
    /** Set for album results. */
    val album: LidarrAlbum? = null
)

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.ALBUMS,
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val lidarrConfigured: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val jellyfin: JellyfinRepository,
    private val lidarr: LidarrRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState(lidarrConfigured = settings.current.hasLidarr))
    val state: StateFlow<SearchUiState> = _state

    /** Results the user has already sent to Lidarr this session, keyed by [SearchResult.key]. */
    private val requested = mutableSetOf<String>()

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun onModeChange(mode: SearchMode) {
        _state.value = _state.value.copy(mode = mode)
        if (_state.value.query.isNotBlank()) search()
    }

    fun refreshConfigFlag() {
        _state.value = _state.value.copy(lidarrConfigured = settings.current.hasLidarr)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    fun search() {
        val s = _state.value
        val term = s.query.trim()
        if (term.isBlank()) return
        if (!settings.current.hasLidarr) {
            _state.value = s.copy(
                error = "Set the Lidarr server URL and API key in Settings first.",
                lidarrConfigured = false
            )
            return
        }
        _state.value = s.copy(isLoading = true, error = null, message = null)

        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    // Both sides are queried in parallel; the Jellyfin side is what
                    // lets each Lidarr result be flagged as already owned.
                    val lidarrDeferred = async {
                        if (s.mode == SearchMode.ARTISTS) {
                            lidarr.lookupArtists(term).map { it.toResult() }
                        } else {
                            lidarr.lookupAlbums(term).map { it.toResult() }
                        }
                    }
                    val jellyfinDeferred = async {
                        val types = if (s.mode == SearchMode.ARTISTS) "MusicArtist" else "MusicAlbum"
                        runCatching { jellyfin.search(term, types) }.getOrDefault(emptyList())
                    }
                    val lidarrResults = lidarrDeferred.await()
                    val owned = jellyfinDeferred.await()
                        .mapNotNull { it.name?.normalizeForMatch() }
                        .toSet()

                    lidarrResults.map { result ->
                        val status = when {
                            requested.contains(result.key) -> LibraryStatus.REQUESTED
                            owned.contains(result.title.normalizeForMatch()) -> LibraryStatus.IN_LIBRARY
                            else -> LibraryStatus.NOT_IN_LIBRARY
                        }
                        result.copy(status = status)
                    }.sortedBy { it.status.ordinal }
                }
            }
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        results = results,
                        error = if (results.isEmpty()) "No matches for \"$term\"" else null
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = t.message ?: "Lidarr search failed"
                    )
                }
        }
    }

    /** Sends a not-in-library result to Lidarr, monitored and with search-on-add. */
    fun request(result: SearchResult) {
        if (result.status != LibraryStatus.NOT_IN_LIBRARY) return
        _state.value = _state.value.copy(isLoading = true, error = null, message = null)

        viewModelScope.launch {
            val outcome = when {
                result.artist != null -> lidarr.addArtist(result.artist).map { Unit }
                result.album != null -> lidarr.addAlbum(result.album).map { Unit }
                else -> Result.failure(IllegalStateException("Nothing to request"))
            }
            outcome
                .onSuccess {
                    requested.add(result.key)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        message = "Requested \"${result.title}\" — Lidarr is searching for it.",
                        results = _state.value.results.map {
                            if (it.key == result.key) it.copy(status = LibraryStatus.REQUESTED) else it
                        }
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = t.message ?: "Could not add to Lidarr"
                    )
                }
        }
    }

    private fun LidarrArtist.toResult() = SearchResult(
        key = "artist:" + (foreignArtistId ?: artistName),
        title = artistName,
        subtitle = disambiguation ?: overview?.take(80).orEmpty(),
        imageUrl = images.firstOrNull { it.coverType == "poster" }?.bestUrl()
            ?: images.firstOrNull()?.bestUrl(),
        status = LibraryStatus.NOT_IN_LIBRARY,
        artist = this
    )

    private fun LidarrAlbum.toResult() = SearchResult(
        key = "album:" + (foreignAlbumId ?: title),
        title = title,
        subtitle = listOfNotNull(
            artist?.artistName,
            albumType,
            releaseDate?.take(4)
        ).joinToString(" · "),
        imageUrl = images.firstOrNull { it.coverType == "cover" }?.bestUrl()
            ?: images.firstOrNull()?.bestUrl(),
        status = LibraryStatus.NOT_IN_LIBRARY,
        album = this
    )

    private fun com.jellyfinmusic.network.LidarrImage.bestUrl(): String? = remoteUrl ?: url
}

/**
 * Jellyfin and Lidarr rarely agree on punctuation or case for the same release,
 * so names are compared with both stripped out.
 */
private fun String.normalizeForMatch(): String =
    lowercase().filter { it.isLetterOrDigit() }
