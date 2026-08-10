package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.ui.ExploreViewModel
import com.jellyfinmusic.ui.components.AlbumCard
import com.jellyfinmusic.ui.components.LoadingRow
import com.jellyfinmusic.ui.components.ShelfHeader
import com.jellyfinmusic.ui.theme.AppColors
import kotlin.math.absoluteValue

@Composable
fun ExploreScreen(
    contentPadding: PaddingValues,
    onAlbumClick: (BaseItem) -> Unit,
    viewModel: ExploreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        if (state.isLoading) item { LoadingRow() }

        state.error?.let {
            item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }

        if (state.newReleases.isNotEmpty()) {
            item { ShelfHeader("New releases") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.newReleases, key = { it.id }) { album ->
                        AlbumCard(
                            title = album.name.orEmpty(),
                            subtitle = album.artistName,
                            artworkUrl = viewModel.imageUrl(album),
                            onClick = { onAlbumClick(album) }
                        )
                    }
                }
            }
        }

        if (state.genres.isNotEmpty()) {
            item { ShelfHeader("Moods & genres") }
            item {
                // Two rows of colour-blocked tiles, scrolling sideways, the way
                // YouTube Music presents its mood categories.
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.genres, key = { it.id }) { genre ->
                        GenreTile(
                            name = genre.name.orEmpty(),
                            selected = state.selectedGenre == genre.name,
                            onClick = { viewModel.selectGenre(genre.name.orEmpty()) }
                        )
                    }
                }
            }
        }

        state.selectedGenre?.let { genre ->
            item { ShelfHeader(genre) }
            if (state.genreAlbums.isEmpty()) {
                item { LoadingRow() }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.genreAlbums, key = { it.id }) { album ->
                            AlbumCard(
                                title = album.name.orEmpty(),
                                subtitle = album.artistName,
                                artworkUrl = viewModel.imageUrl(album),
                                onClick = { onAlbumClick(album) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Genre tiles get a stable colour derived from the name's hash, so the palette
 * looks deliberate and a genre keeps the same colour between launches.
 */
@Composable
private fun GenreTile(name: String, selected: Boolean, onClick: () -> Unit) {
    val palette = listOf(
        Color(0xFF8E44AD), Color(0xFF16A085), Color(0xFFD35400), Color(0xFF2980B9),
        Color(0xFFC0392B), Color(0xFF27AE60), Color(0xFF7F5AF0), Color(0xFFE67E22)
    )
    val color = palette[(name.hashCode().absoluteValue) % palette.size]

    Box(
        Modifier
            .width(160.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) AppColors.OnBackground else color)
            .clickable(onClick = onClick)
            .padding(10.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) AppColors.Background else Color.White,
            maxLines = 2
        )
    }
}
