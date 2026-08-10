package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.ui.LibraryStatus
import com.jellyfinmusic.ui.RequestResult
import com.jellyfinmusic.ui.SearchFilter
import com.jellyfinmusic.ui.SearchViewModel
import com.jellyfinmusic.ui.components.Artwork
import com.jellyfinmusic.ui.components.PillRow
import com.jellyfinmusic.ui.components.ShelfHeader
import com.jellyfinmusic.ui.components.TrackRow
import com.jellyfinmusic.ui.components.formatDuration
import com.jellyfinmusic.ui.theme.AppColors

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onAlbumClick: (BaseItem) -> Unit,
    onArtistClick: (BaseItem) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { viewModel.refreshConfigFlag() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        TextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Songs, albums and artists", color = AppColors.Secondary) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { viewModel.clearQuery() }
                            .padding(4.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                viewModel.search()
            }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.SurfaceVariant,
                unfocusedContainerColor = AppColors.SurfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (state.submitted) {
            PillRow(
                options = SearchFilter.entries.map { it.label },
                selected = state.filter.label,
                onSelect = { viewModel.onFilterChange(SearchFilter.fromLabel(it)) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        state.message?.let {
            Text(
                it,
                color = AppColors.Accent,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val bottom = contentPadding.calculateBottomPadding() + 24.dp

        if (!state.submitted) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Search your library — or use \"Get more\"\nto request music you don't have yet.",
                    color = AppColors.Secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottom)
        ) {
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        CircularProgressIndicator(
                            Modifier.size(24.dp),
                            color = AppColors.OnBackground,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            val showLibrary = state.filter != SearchFilter.REQUEST

            if (showLibrary && state.filter == SearchFilter.ALL) {
                state.topResult?.let { top ->
                    item { ShelfHeader("Top result") }
                    item {
                        TopResultCard(
                            item = top,
                            artworkUrl = viewModel.imageUrl(top),
                            onClick = {
                                when (top.type) {
                                    "MusicArtist" -> onArtistClick(top)
                                    "MusicAlbum" -> onAlbumClick(top)
                                    else -> viewModel.playSong(state.songs.indexOf(top).coerceAtLeast(0))
                                }
                            }
                        )
                    }
                }
            }

            if (showLibrary &&
                state.songs.isNotEmpty() &&
                (state.filter == SearchFilter.ALL || state.filter == SearchFilter.SONGS)
            ) {
                item { ShelfHeader("Songs") }
                itemsIndexed(state.songs, key = { _, s -> "song-" + s.id }) { index, song ->
                    TrackRow(
                        title = song.name.orEmpty(),
                        subtitle = listOfNotNull(
                            song.artistName,
                            formatDuration(song.durationMs)
                        ).joinToString(" · "),
                        artworkUrl = viewModel.imageUrl(song),
                        onClick = { viewModel.playSong(index) },
                        onMenuClick = { viewModel.showMenu(song) },
                        isFavorite = song.id in favorites,
                        onFavoriteClick = { viewModel.toggleFavorite(song) }
                    )
                }
            }

            if (showLibrary &&
                state.albums.isNotEmpty() &&
                (state.filter == SearchFilter.ALL || state.filter == SearchFilter.ALBUMS)
            ) {
                item { ShelfHeader("Albums") }
                itemsIndexed(state.albums, key = { _, a -> "album-" + a.id }) { _, album ->
                    TrackRow(
                        title = album.name.orEmpty(),
                        subtitle = listOfNotNull("Album", album.artistName).joinToString(" · "),
                        artworkUrl = viewModel.imageUrl(album),
                        onClick = { onAlbumClick(album) }
                    )
                }
            }

            if (showLibrary &&
                state.artists.isNotEmpty() &&
                (state.filter == SearchFilter.ALL || state.filter == SearchFilter.ARTISTS)
            ) {
                item { ShelfHeader("Artists") }
                itemsIndexed(state.artists, key = { _, a -> "artist-" + a.id }) { _, artist ->
                    TrackRow(
                        title = artist.name.orEmpty(),
                        subtitle = "Artist",
                        artworkUrl = viewModel.imageUrl(artist),
                        onClick = { onArtistClick(artist) },
                        isArtist = true,
                        artShape = CircleShape
                    )
                }
            }

            if (showLibrary && !state.hasLibraryResults && !state.isLoading) {
                item {
                    Column(Modifier.fillMaxWidth().padding(24.dp)) {
                        Text("Nothing in your library matches that.", color = AppColors.Secondary)
                        Text(
                            "Try the \"Get more\" tab to request it.",
                            color = AppColors.Secondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (state.filter == SearchFilter.REQUEST) {
                if (!state.lidarrConfigured) {
                    item {
                        Text(
                            "Add your Lidarr server URL and API key in Settings to request music.",
                            color = AppColors.Secondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    item {
                        Text(
                            "Lidarr grabs whole albums, so requesting a song adds " +
                                "the album it appears on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Secondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    if (state.isRequestLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                                CircularProgressIndicator(
                                    Modifier.size(24.dp),
                                    color = AppColors.OnBackground,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    itemsIndexed(state.requestResults, key = { _, r -> r.key }) { _, result ->
                        RequestRow(result) { viewModel.request(result) }
                    }
                    if (state.requestResults.isEmpty() && !state.isRequestLoading) {
                        item {
                            Text(
                                "No matches on Lidarr.",
                                color = AppColors.Secondary,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The oversized first hit YouTube Music puts above the result lists. */
@Composable
private fun TopResultCard(item: BaseItem, artworkUrl: String?, onClick: () -> Unit) {
    val isArtist = item.type == "MusicArtist"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(
            artworkUrl,
            Modifier.size(90.dp),
            shape = if (isArtist) CircleShape else RoundedCornerShape(6.dp),
            isArtist = isArtist
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                item.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
            Text(
                when (item.type) {
                    "MusicArtist" -> "Artist"
                    "MusicAlbum" -> listOfNotNull("Album", item.artistName).joinToString(" · ")
                    else -> listOfNotNull("Song", item.artistName).joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = AppColors.OnBackground,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceVariant)
                .padding(6.dp)
        )
    }
}

@Composable
private fun RequestRow(result: RequestResult, onRequest: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(result.imageUrl, Modifier.size(48.dp), shape = RoundedCornerShape(4.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(result.title, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
            Text(
                result.subtitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary
            )
        }
        when (result.status) {
            LibraryStatus.IN_LIBRARY -> StatusPill(
                text = "In library",
                icon = Icons.Filled.CheckCircle,
                background = AppColors.SurfaceVariant,
                foreground = AppColors.Secondary,
                onClick = null
            )

            LibraryStatus.REQUESTED -> StatusPill(
                text = "Requested",
                icon = Icons.Filled.HourglassTop,
                background = AppColors.SurfaceVariant,
                foreground = AppColors.Accent,
                onClick = null
            )

            LibraryStatus.NOT_IN_LIBRARY -> StatusPill(
                text = "Request",
                icon = Icons.Filled.Add,
                background = AppColors.OnBackground,
                foreground = AppColors.Background,
                onClick = onRequest
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    foreground: Color,
    onClick: (() -> Unit)?
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
        Text(
            text,
            color = foreground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
