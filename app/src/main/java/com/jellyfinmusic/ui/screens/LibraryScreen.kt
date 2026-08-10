package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.ui.LibraryTab
import com.jellyfinmusic.ui.LibraryViewModel
import com.jellyfinmusic.ui.components.AlbumCard
import com.jellyfinmusic.ui.components.ArtistCard
import com.jellyfinmusic.ui.components.PillRow
import com.jellyfinmusic.ui.components.StateBox
import com.jellyfinmusic.ui.components.TrackRow
import com.jellyfinmusic.ui.components.formatDuration
import com.jellyfinmusic.ui.theme.AppColors

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    onAlbumClick: (BaseItem) -> Unit,
    onArtistClick: (BaseItem) -> Unit,
    onPlaylistClick: (BaseItem) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            PillRow(
                options = LibraryTab.entries.map { it.label },
                selected = state.tab.label,
                onSelect = { viewModel.select(LibraryTab.fromLabel(it)) },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = viewModel::toggleLayout) {
                Icon(
                    if (state.isGrid) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                    contentDescription = "Toggle layout",
                    tint = AppColors.Secondary
                )
            }
        }

        val bottom = contentPadding.calculateBottomPadding() + 16.dp

        StateBox(
            isLoading = state.isLoading,
            error = state.error,
            isEmpty = state.items.isEmpty(),
            emptyMessage = "Nothing here yet",
            modifier = Modifier.weight(1f)
        ) {
            // Songs are always a list; everything else honours the grid toggle,
            // since artwork is the point for albums, artists and playlists.
            if (state.tab == LibraryTab.SONGS || !state.isGrid) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottom)
                ) {
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                        TrackRow(
                            title = item.name.orEmpty(),
                            subtitle = subtitleFor(item, state.tab),
                            artworkUrl = viewModel.imageUrl(item),
                            onClick = { onItemClick(item, state.tab, onAlbumClick, onArtistClick, onPlaylistClick) { viewModel.playSongs(index) } },
                            isArtist = state.tab == LibraryTab.ARTISTS,
                            artShape = if (state.tab == LibraryTab.ARTISTS) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottom),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { it.id }) { item ->
                        if (state.tab == LibraryTab.ARTISTS) {
                            Box(Modifier.fillMaxWidth(), androidx.compose.ui.Alignment.Center) {
                                ArtistCard(
                                    name = item.name.orEmpty(),
                                    artworkUrl = viewModel.imageUrl(item),
                                    onClick = { onArtistClick(item) },
                                    size = 150
                                )
                            }
                        } else {
                            AlbumCard(
                                title = item.name.orEmpty(),
                                subtitle = subtitleFor(item, state.tab),
                                artworkUrl = viewModel.imageUrl(item),
                                onClick = {
                                    if (state.tab == LibraryTab.PLAYLISTS) onPlaylistClick(item)
                                    else onAlbumClick(item)
                                },
                                width = 170
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun subtitleFor(item: BaseItem, tab: LibraryTab): String = when (tab) {
    LibraryTab.PLAYLISTS -> item.childCount?.let { "Playlist · $it tracks" } ?: "Playlist"
    LibraryTab.ALBUMS -> listOfNotNull(item.artistName, item.productionYear?.toString()).joinToString(" · ")
    LibraryTab.ARTISTS -> "Artist"
    LibraryTab.SONGS -> listOfNotNull(
        item.artistName,
        formatDuration(item.durationMs)
    ).joinToString(" · ")
}

private inline fun onItemClick(
    item: BaseItem,
    tab: LibraryTab,
    onAlbumClick: (BaseItem) -> Unit,
    onArtistClick: (BaseItem) -> Unit,
    onPlaylistClick: (BaseItem) -> Unit,
    onSongClick: () -> Unit
) {
    when (tab) {
        LibraryTab.PLAYLISTS -> onPlaylistClick(item)
        LibraryTab.ALBUMS -> onAlbumClick(item)
        LibraryTab.ARTISTS -> onArtistClick(item)
        LibraryTab.SONGS -> onSongClick()
    }
}
