package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    onPlaylistClick: (BaseItem) -> Unit,
    onLikedSongsClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }

    Box(Modifier.fillMaxSize()) {
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
            if (state.tab == LibraryTab.PLAYLISTS) {
                IconButton(onClick = viewModel::newPlaylist) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "New playlist",
                        tint = AppColors.OnBackground
                    )
                }
            }
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
            val isTrackList = state.tab == LibraryTab.SONGS || state.tab == LibraryTab.DOWNLOADS
            if (isTrackList || !state.isGrid) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = bottom)
                ) {
                    if (state.tab == LibraryTab.PLAYLISTS) {
                        item { LikedSongsRow(onClick = onLikedSongsClick) }
                    }
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                        TrackRow(
                            title = item.name.orEmpty(),
                            subtitle = subtitleFor(item, state.tab),
                            artworkUrl = viewModel.imageUrl(item),
                            onClick = {
                                onItemClick(
                                    item,
                                    state.tab,
                                    onAlbumClick,
                                    onArtistClick,
                                    onPlaylistClick
                                ) { viewModel.playSongs(index) }
                            },
                            isArtist = state.tab == LibraryTab.ARTISTS,
                            artShape = if (state.tab == LibraryTab.ARTISTS) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            isFavorite = item.id in favorites,
                            onFavoriteClick = if (isTrackList) {
                                { viewModel.toggleFavorite(item) }
                            } else {
                                null
                            },
                            onMenuClick = if (isTrackList) {
                                { viewModel.showMenu(item) }
                            } else {
                                null
                            }
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
                    if (state.tab == LibraryTab.PLAYLISTS) {
                        item { LikedSongsCard(onClick = onLikedSongsClick) }
                    }
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

        // Floating shuffle, as YouTube Music offers over its library lists.
        val isTrackTab = state.tab == LibraryTab.SONGS || state.tab == LibraryTab.DOWNLOADS
        if (isTrackTab && state.items.isNotEmpty()) {
            Row(
                Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 16.dp
                    )
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .background(AppColors.OnBackground)
                    .clickable { viewModel.shuffleAll() }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = null,
                    tint = AppColors.Background,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "Shuffle all",
                    color = AppColors.Background,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun subtitleFor(item: BaseItem, tab: LibraryTab): String = when (tab) {
    LibraryTab.DOWNLOADS -> listOfNotNull(item.artistName, "Downloaded").joinToString(" · ")
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
        LibraryTab.DOWNLOADS -> onSongClick()
        LibraryTab.PLAYLISTS -> onPlaylistClick(item)
        LibraryTab.ALBUMS -> onAlbumClick(item)
        LibraryTab.ARTISTS -> onArtistClick(item)
        LibraryTab.SONGS -> onSongClick()
    }
}

/** Pinned entry that opens the favourites view, styled like a playlist. */
@Composable
private fun LikedSongsRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFF107A3)))
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text("Liked songs")
            Text(
                "Auto playlist",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary
            )
        }
    }
}

@Composable
private fun LikedSongsCard(onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF7B2FF7), Color(0xFFF107A3)))
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        Text("Liked songs", modifier = Modifier.padding(top = 8.dp))
        Text(
            "Auto playlist",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Secondary
        )
    }
}
