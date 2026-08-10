package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.LidarrRepository
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.data.imports.ImportSource
import com.jellyfinmusic.data.imports.MatchQuality
import com.jellyfinmusic.data.imports.MatchedTrack
import com.jellyfinmusic.data.imports.PlaylistImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImportUiState(
    val source: ImportSource = ImportSource.M3U,
    val url: String = "",
    val pastedText: String = "",
    val playlistName: String = "",
    val matched: List<MatchedTrack> = emptyList(),
    val isWorking: Boolean = false,
    val progress: Pair<Int, Int>? = null,
    val status: String? = null,
    val error: String? = null,
    val finished: Boolean = false,
    val requestedKeys: Set<String> = emptySet(),
    val hasSpotify: Boolean = false,
    val hasYouTube: Boolean = false,
    val hasLidarr: Boolean = false
) {
    val found: List<MatchedTrack> get() = matched.filter { it.match != null }
    val missing: List<MatchedTrack> get() = matched.filter { it.match == null }
    val selectedCount: Int get() = matched.count { it.include }
}

/**
 * Drives playlist import: read a source, match it against the library, let the
 * user correct the result, then create the playlist — and hand anything missing
 * to Lidarr, which is the part no streaming client can do.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: PlaylistImporter,
    private val lidarr: LidarrRepository,
    private val settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state

    fun refreshCapabilities() {
        val s = settings.current
        _state.value = _state.value.copy(
            hasSpotify = s.hasSpotify,
            hasYouTube = s.hasYouTube,
            hasLidarr = s.hasLidarr
        )
    }

    fun setSource(source: ImportSource) {
        _state.value = _state.value.copy(source = source, error = null, status = null)
    }

    fun setUrl(url: String) { _state.value = _state.value.copy(url = url) }
    fun setPastedText(text: String) { _state.value = _state.value.copy(pastedText = text) }
    fun setPlaylistName(name: String) { _state.value = _state.value.copy(playlistName = name) }
    fun dismissMessages() { _state.value = _state.value.copy(error = null, status = null) }

    /** Called with the contents of a file the user picked. */
    fun importFile(fileName: String, content: String) {
        run { importer.parseFile(fileName, content) }
    }

    fun importPastedText() {
        val text = _state.value.pastedText
        if (text.isBlank()) return
        run { importer.parseText(text) }
    }

    fun importFromUrl() {
        val url = _state.value.url.trim()
        if (url.isBlank()) return
        val source = _state.value.source
        runSuspending {
            when (source) {
                ImportSource.SPOTIFY -> importer.fetchSpotify(url)
                ImportSource.YOUTUBE -> importer.fetchYouTube(url)
                else -> error("That source does not take a link")
            }
        }
    }

    fun toggle(index: Int) {
        _state.value = _state.value.copy(
            matched = _state.value.matched.mapIndexed { i, track ->
                if (i == index && track.match != null) track.copy(include = !track.include) else track
            }
        )
    }

    fun selectAllFound(include: Boolean) {
        _state.value = _state.value.copy(
            matched = _state.value.matched.map {
                if (it.match != null) it.copy(include = include) else it
            }
        )
    }

    fun createPlaylist() {
        val name = _state.value.playlistName.trim().ifBlank { "Imported playlist" }
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            runCatching { importer.createPlaylist(name, _state.value.matched) }
                .onSuccess { count ->
                    _state.value = _state.value.copy(
                        isWorking = false,
                        finished = true,
                        status = "Created \"$name\" with $count tracks"
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isWorking = false,
                        error = it.message ?: "Could not create the playlist"
                    )
                }
        }
    }

    /**
     * Sends a track the library does not have to Lidarr, resolving it through
     * MusicBrainz to the album Lidarr can actually fetch.
     */
    fun requestMissing(track: MatchedTrack) {
        if (!settings.current.hasLidarr) {
            _state.value = _state.value.copy(error = "Add Lidarr in Settings to request music")
            return
        }
        val key = track.source.display
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            val query = listOf(track.source.artist, track.source.title)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            val song = runCatching { lidarr.lookupSongs(query).firstOrNull() }.getOrNull()
            if (song == null) {
                _state.value = _state.value.copy(
                    isWorking = false,
                    error = "No match on MusicBrainz for \"${track.source.display}\""
                )
                return@launch
            }
            val album = runCatching { lidarr.resolveAlbumForSong(song) }.getOrNull()
            if (album == null) {
                _state.value = _state.value.copy(
                    isWorking = false,
                    error = "Lidarr could not find \"${song.albumTitle}\""
                )
                return@launch
            }
            lidarr.addAlbum(album)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isWorking = false,
                        requestedKeys = _state.value.requestedKeys + key,
                        status = "Requested \"${song.albumTitle}\""
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isWorking = false,
                        error = it.message ?: "Could not add to Lidarr"
                    )
                }
        }
    }

    fun requestAllMissing() {
        _state.value.missing
            .filterNot { it.source.display in _state.value.requestedKeys }
            .take(BULK_REQUEST_LIMIT)
            .forEach(::requestMissing)
    }

    fun reset() {
        _state.value = ImportUiState(
            hasSpotify = settings.current.hasSpotify,
            hasYouTube = settings.current.hasYouTube,
            hasLidarr = settings.current.hasLidarr
        )
    }

    private fun run(block: () -> com.jellyfinmusic.data.imports.SourcePlaylist) =
        runSuspending { block() }

    private fun runSuspending(
        block: suspend () -> com.jellyfinmusic.data.imports.SourcePlaylist
    ) {
        _state.value = _state.value.copy(isWorking = true, error = null, status = null)
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { source ->
                    if (source.tracks.isEmpty()) {
                        _state.value = _state.value.copy(
                            isWorking = false,
                            error = "No tracks found in that playlist"
                        )
                        return@onSuccess
                    }
                    _state.value = _state.value.copy(
                        playlistName = _state.value.playlistName.ifBlank { source.name },
                        progress = 0 to source.tracks.size
                    )
                    val matched = importer.matchAll(source.tracks) { done, total ->
                        _state.value = _state.value.copy(progress = done to total)
                    }
                    _state.value = _state.value.copy(
                        isWorking = false,
                        progress = null,
                        matched = matched,
                        status = "${matched.count { it.match != null }} of ${matched.size} " +
                            "found in your library"
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isWorking = false,
                        progress = null,
                        error = it.message ?: "Could not read that playlist"
                    )
                }
        }
    }

    private companion object {
        /** Guards against firing hundreds of Lidarr adds from one tap. */
        const val BULK_REQUEST_LIMIT = 25
    }
}

/** Label for how good a match is, used by the UI. */
fun MatchQuality.label(): String = when (this) {
    MatchQuality.EXACT -> "Exact"
    MatchQuality.CLOSE -> "Close"
    MatchQuality.WEAK -> "Uncertain"
    MatchQuality.NONE -> "Not in library"
}
