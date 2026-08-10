package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.ui.HomeViewModel
import com.jellyfinmusic.ui.components.AlbumCard
import com.jellyfinmusic.ui.components.ArtistCard
import com.jellyfinmusic.ui.components.Artwork
import com.jellyfinmusic.ui.components.LoadingRow
import com.jellyfinmusic.ui.components.Pill
import com.jellyfinmusic.ui.components.ShelfHeader
import com.jellyfinmusic.ui.components.TrackRow
import com.jellyfinmusic.ui.theme.AppColors

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onAlbumClick: (BaseItem) -> Unit,
    onArtistClick: (BaseItem) -> Unit,
    onPlaylistClick: (BaseItem) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val dismissed by viewModel.dismissedIds.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        // Mood chips ride at the very top, where YouTube Music puts them.
        if (state.moods.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    items(state.moods, key = { it.id }) { mood ->
                        Pill(
                            text = mood.name.orEmpty(),
                            selected = false,
                            onClick = { viewModel.startGenreRadio(mood) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                state.greeting,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
        }

        if (state.speedDial.isNotEmpty()) {
            item { ShelfHeader("Speed dial", subtitle = "WHAT YOU HAVE BEEN NEAR LATELY") }
            item {
                SpeedDialGrid(
                    items = state.speedDial.filterNot { it.id in dismissed },
                    artworkFor = viewModel::imageUrl,
                    onClick = { item ->
                        when (item.type) {
                            "MusicArtist" -> onArtistClick(item)
                            "Playlist" -> onPlaylistClick(item)
                            else -> onAlbumClick(item)
                        }
                    }
                )
            }
        }

        if (state.continueListening.isNotEmpty()) {
            item { ShelfHeader("Continue listening", subtitle = "ALBUMS YOU DID NOT FINISH") }
            item {
                CardShelf(state.continueListening.filterNot { it.id in dismissed }) { album ->
                    AlbumCard(
                        title = album.name.orEmpty(),
                        subtitle = album.artistName,
                        artworkUrl = viewModel.imageUrl(album),
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }

        if (state.isLoading) {
            item { LoadingRow() }
        }

        state.error?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (state.quickPicks.isNotEmpty()) {
            item { ShelfHeader("Quick picks", subtitle = "START RADIO FROM A SONG") }
            item {
                QuickPicksPager(
                    tracks = state.quickPicks,
                    artworkFor = viewModel::imageUrl,
                    favorites = favorites,
                    onTrackClick = { viewModel.playQuickPicks(it) },
                    onMenuClick = viewModel::showMenu,
                    onFavoriteClick = viewModel::toggleFavorite
                )
            }
        }

        if (state.listenAgain.isNotEmpty()) {
            item { ShelfHeader("Listen again") }
            item {
                CardShelf(state.listenAgain) { album ->
                    AlbumCard(
                        title = album.name.orEmpty(),
                        subtitle = album.artistName,
                        artworkUrl = viewModel.imageUrl(album),
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }

        if (state.newReleases.isNotEmpty()) {
            item { ShelfHeader("New releases", subtitle = "RECENTLY ADDED TO YOUR SERVER") }
            item {
                CardShelf(state.newReleases) { album ->
                    AlbumCard(
                        title = album.name.orEmpty(),
                        subtitle = album.artistName,
                        artworkUrl = viewModel.imageUrl(album),
                        onClick = { onAlbumClick(album) }
                    )
                }
            }
        }

        if (state.artists.isNotEmpty()) {
            item { ShelfHeader("Artists you have") }
            item {
                CardShelf(state.artists) { artist ->
                    ArtistCard(
                        name = artist.name.orEmpty(),
                        artworkUrl = viewModel.imageUrl(artist),
                        onClick = { onArtistClick(artist) }
                    )
                }
            }
        }

        if (state.topSongs.isNotEmpty()) {
            item { ShelfHeader("Your top songs", subtitle = "MOST PLAYED ON THIS SERVER") }
            itemsIndexed(
                state.topSongs.filterNot { it.id in dismissed },
                key = { _, track -> "top-" + track.id }
            ) { index, track ->
                TrackRow(
                    title = track.name.orEmpty(),
                    subtitle = track.artistName.orEmpty(),
                    artworkUrl = viewModel.imageUrl(track),
                    onClick = { viewModel.playTopSong(index) },
                    onMenuClick = { viewModel.showMenu(track) },
                    isFavorite = track.id in favorites,
                    onFavoriteClick = { viewModel.toggleFavorite(track) }
                )
            }
        }

        if (state.playlists.isNotEmpty()) {
            item { ShelfHeader("Your playlists") }
            item {
                CardShelf(state.playlists) { playlist ->
                    AlbumCard(
                        title = playlist.name.orEmpty(),
                        subtitle = playlist.childCount?.let { "$it tracks" },
                        artworkUrl = viewModel.imageUrl(playlist),
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
/**
 * YouTube Music's Quick picks: a horizontally paged grid where each page holds
 * four stacked track rows and the next page peeks in from the right edge.
 */
@Composable
private fun QuickPicksPager(
    tracks: List<BaseItem>,
    artworkFor: (BaseItem) -> String?,
    favorites: Set<String>,
    onTrackClick: (Int) -> Unit,
    onMenuClick: (BaseItem) -> Unit,
    onFavoriteClick: (BaseItem) -> Unit
) {
    val rowsPerPage = 4
    val pages = tracks.chunked(rowsPerPage)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    HorizontalPager(
        state = pagerState,
        pageSize = androidx.compose.foundation.pager.PageSize.Fixed(screenWidth - 48.dp),
        contentPadding = PaddingValues(end = 40.dp),
        pageSpacing = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) { page ->
        Column {
            pages[page].forEachIndexed { row, track ->
                val globalIndex = page * rowsPerPage + row
                TrackRow(
                    title = track.name.orEmpty(),
                    subtitle = track.artistName.orEmpty(),
                    artworkUrl = artworkFor(track),
                    onClick = { onTrackClick(globalIndex) },
                    artSize = 52,
                    onMenuClick = { onMenuClick(track) },
                    isFavorite = track.id in favorites,
                    onFavoriteClick = { onFavoriteClick(track) }
                )
            }
        }
    }
}

@Composable
private fun <T> CardShelf(
    items: List<T>,
    content: @Composable (T) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item -> content(item) }
    }
}

/**
 * Speed dial: a 3x3 paged grid of whatever the listener has been near, mixing
 * albums, artists and playlists rather than separating them by type.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialGrid(
    items: List<BaseItem>,
    artworkFor: (BaseItem) -> String?,
    onClick: (BaseItem) -> Unit
) {
    if (items.isEmpty()) return
    val perPage = 9
    val pages = items.chunked(perPage)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val pageWidth = screenWidth - 32.dp
    val cell = (pageWidth - 16.dp) / 3

    Column {
        HorizontalPager(
            state = pagerState,
            pageSize = androidx.compose.foundation.pager.PageSize.Fixed(pageWidth),
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                pages[page].chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { item ->
                            SpeedDialCell(item, artworkFor(item), cell) { onClick(item) }
                        }
                    }
                }
            }
        }

        if (pages.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) AppColors.OnBackground
                                else AppColors.SurfaceVariant
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedDialCell(
    item: BaseItem,
    artworkUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Artwork(
            artworkUrl,
            Modifier.fillMaxSize(),
            shape = RoundedCornerShape(6.dp),
            isArtist = item.type == "MusicArtist"
        )
        // A scrim keeps the label readable over any artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f)
                    )
                )
        )
        Text(
            item.name.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )
    }
}
