package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * Liked songs: not a real Jellyfin playlist but a view over the user's
 * favourited tracks, presented like one.
 */
@Composable
fun LikedSongsScreen(
    contentPadding: PaddingValues,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadLikedSongs() }

    StateBox(state.isLoading, state.error, false, "") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF7B2FF7), AppColors.Background)
                            )
                        )
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Liked songs",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        "${state.tracks.size} song${if (state.tracks.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Secondary
                    )
                    Row(
                        Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.playAll(shuffle = false) },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.OnBackground,
                                contentColor = AppColors.Background
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, Modifier.size(20.dp))
                            Text("Play", Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { viewModel.playAll(shuffle = true) },
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, Modifier.size(18.dp))
                            Text("Shuffle", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

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
                            "Songs you like will show up here.\nTap the heart on any track.",
                            color = AppColors.Secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
