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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.ui.RecapViewModel
import com.jellyfinmusic.ui.components.ShelfHeader
import com.jellyfinmusic.ui.components.StateBox
import com.jellyfinmusic.ui.components.TrackRow
import com.jellyfinmusic.ui.theme.AppColors

/**
 * A listening summary from the server's play counts.
 *
 * Jellyfin keeps a running count per track rather than a dated history, so this
 * is a lifetime picture rather than a calendar year — and it only reflects what
 * clients have actually reported.
 */
@Composable
fun RecapScreen(
    contentPadding: PaddingValues,
    viewModel: RecapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadOnce() }

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
                                listOf(Color(0xFF0C6B63), AppColors.Background)
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Your listening", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Everything this server has counted so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Secondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Stat(state.totalPlays.toString(), "plays")
                        Stat(state.distinctTracks.toString(), "tracks")
                        Stat(state.minutes.formatMinutes(), "listened")
                    }
                }
            }

            if (state.topArtists.isNotEmpty()) {
                item { ShelfHeader("Top artists") }
                itemsIndexed(state.topArtists) { index, (name, plays) ->
                    RankRow(index + 1, name, "$plays plays")
                }
            }

            if (state.topAlbums.isNotEmpty()) {
                item { ShelfHeader("Top albums") }
                itemsIndexed(state.topAlbums) { index, (name, plays) ->
                    RankRow(index + 1, name, "$plays plays")
                }
            }

            if (state.topSongs.isNotEmpty()) {
                item { ShelfHeader("Top songs") }
                itemsIndexed(state.topSongs, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        title = track.name.orEmpty(),
                        subtitle = listOfNotNull(
                            track.artistName,
                            track.userData?.playCount?.let { "$it plays" }
                        ).joinToString(" · "),
                        artworkUrl = viewModel.imageUrl(track),
                        onClick = { viewModel.playTopSong(index) }
                    )
                }
            }

            if (state.totalPlays == 0 && !state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        Text(
                            "Nothing counted yet.\nThis fills in as you listen.",
                            color = AppColors.Secondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
    }
}

/** Numbered because a chart is genuinely an ordered thing. */
@Composable
private fun RankRow(rank: Int, name: String, detail: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(name, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
        }
    }
}

private fun Long.formatMinutes(): String =
    if (this >= 60) "${this / 60}h" else "${this}m"
