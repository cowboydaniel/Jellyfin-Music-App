package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.ui.DetailViewModel
import com.jellyfinmusic.ui.components.StateBox
import com.jellyfinmusic.ui.components.TrackRow
import com.jellyfinmusic.ui.components.formatDuration
import com.jellyfinmusic.ui.theme.AppColors

/**
 * Everything played recently, newest first.
 *
 * This is built entirely from what the app reports back to Jellyfin, so it only
 * covers listening since play reporting was added — and it fills up from there.
 */
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadHistory() }

    StateBox(state.isLoading, state.error, false, "") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                TrackRow(
                    title = track.name.orEmpty(),
                    subtitle = listOfNotNull(
                        track.artistName,
                        formatDuration(track.durationMs)
                    ).joinToString(" · "),
                    artworkUrl = viewModel.imageUrl(track),
                    onClick = { viewModel.play(index) },
                    onMenuClick = { viewModel.showMenu(track) },
                    isFavorite = track.id in favorites,
                    onFavoriteClick = { viewModel.toggleFavorite(track) }
                )
            }

            if (state.tracks.isEmpty() && !state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text(
                            "Nothing played yet.\nHistory builds up as you listen.",
                            color = AppColors.Secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
