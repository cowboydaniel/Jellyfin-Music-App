package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.ui.HomeViewModel
import com.jellyfinmusic.ui.components.AlbumCard
import com.jellyfinmusic.ui.components.ArtistCard
import com.jellyfinmusic.ui.components.LoadingRow
import com.jellyfinmusic.ui.components.ShelfHeader
import com.jellyfinmusic.ui.components.TrackRow

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
    LaunchedEffect(Unit) { viewModel.loadOnce() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        item {
            Text(
                state.greeting,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
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
