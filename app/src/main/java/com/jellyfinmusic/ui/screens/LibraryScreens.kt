package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.ui.LibraryViewModel
import com.jellyfinmusic.ui.components.MediaRow
import com.jellyfinmusic.ui.components.StateBox
import com.jellyfinmusic.ui.components.formatDuration

@Composable
fun ArtistsScreen(
    viewModel: LibraryViewModel,
    onArtistClick: (id: String, name: String) -> Unit,
    contentPadding: PaddingValues
) {
    val state by viewModel.artists.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadArtists() }

    StateBox(state.isLoading, state.error, state.items.isEmpty(), "No artists in this library") {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(state.items, key = { it.id }) { artist ->
                MediaRow(
                    title = artist.name.orEmpty(),
                    subtitle = null,
                    artworkUrl = viewModel.imageUrl(artist),
                    modifier = Modifier.clickable { onArtistClick(artist.id, artist.name.orEmpty()) }
                )
            }
        }
    }
}

@Composable
fun AlbumsScreen(
    viewModel: LibraryViewModel,
    artistId: String,
    onAlbumClick: (id: String, name: String) -> Unit,
    contentPadding: PaddingValues
) {
    val state by viewModel.albums.collectAsStateWithLifecycle()
    LaunchedEffect(artistId) { viewModel.loadAlbums(artistId) }

    StateBox(state.isLoading, state.error, state.items.isEmpty(), "No albums for this artist") {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(state.items, key = { it.id }) { album ->
                MediaRow(
                    title = album.name.orEmpty(),
                    subtitle = album.productionYear?.toString(),
                    artworkUrl = viewModel.imageUrl(album),
                    modifier = Modifier.clickable { onAlbumClick(album.id, album.name.orEmpty()) }
                )
            }
        }
    }
}

/**
 * Track list for either an album or a playlist — both are just an ordered list
 * of Audio items, and tapping one plays the whole list from that point.
 */
@Composable
fun TracksScreen(
    viewModel: LibraryViewModel,
    sourceId: String,
    isPlaylist: Boolean,
    contentPadding: PaddingValues
) {
    val state by viewModel.tracks.collectAsStateWithLifecycle()
    LaunchedEffect(sourceId, isPlaylist) {
        if (isPlaylist) viewModel.loadPlaylistTracks(sourceId) else viewModel.loadTracks(sourceId)
    }

    StateBox(state.isLoading, state.error, state.items.isEmpty(), "No tracks here") {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = { viewModel.playTracks(state.items, 0) }) {
                        Text("Play all")
                    }
                    OutlinedButton(
                        onClick = { viewModel.playShuffled(state.items) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Text("Shuffle", Modifier.padding(start = 6.dp))
                    }
                }
            }
            items(state.items, key = { it.id }) { track ->
                val index = state.items.indexOf(track)
                MediaRow(
                    title = track.name.orEmpty(),
                    subtitle = listOfNotNull(
                        track.artistName,
                        formatDuration(track.durationMs)
                    ).joinToString(" · "),
                    artworkUrl = viewModel.imageUrl(track),
                    modifier = Modifier.clickable { viewModel.playTracks(state.items, index) },
                    trailing = {
                        IconButton(onClick = { viewModel.addToQueue(track) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to queue"
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    viewModel: LibraryViewModel,
    onPlaylistClick: (id: String, name: String) -> Unit,
    contentPadding: PaddingValues
) {
    val state by viewModel.playlists.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadPlaylists() }

    StateBox(state.isLoading, state.error, state.items.isEmpty(), "No playlists on this server") {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            items(state.items, key = { it.id }) { playlist ->
                MediaRow(
                    title = playlist.name.orEmpty(),
                    subtitle = playlist.childCount?.let { "$it tracks" },
                    artworkUrl = viewModel.imageUrl(playlist),
                    modifier = Modifier.clickable {
                        onPlaylistClick(playlist.id, playlist.name.orEmpty())
                    }
                )
            }
        }
    }
}

@Composable
fun LibraryHeader(text: String) {
    Column(Modifier.padding(16.dp)) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
    }
}
