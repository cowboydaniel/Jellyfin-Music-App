package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.jellyfinmusic.playback.PlayerConnection
import com.jellyfinmusic.playback.PlayerUiState
import com.jellyfinmusic.ui.components.Artwork
import com.jellyfinmusic.ui.components.formatDuration

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    player: PlayerConnection,
    onCollapse: () -> Unit
) {
    var showQueue by remember { mutableStateOf(false) }
    // While the user drags, the slider follows the finger rather than the player,
    // which would otherwise fight the gesture on every position tick.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Close")
            }
            Text(
                "Now playing",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showQueue = !showQueue }) {
                Text(if (showQueue) "Art" else "Queue", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (showQueue) {
            QueueList(state, player, Modifier.weight(1f))
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Artwork(
                    state.artworkUrl,
                    Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f),
                    cornerRadius = 16
                )
            }
        }

        Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2)
        Text(
            listOf(state.artist, state.album).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )

        val duration = state.durationMs.coerceAtLeast(1L)
        val sliderValue = scrubPosition ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        Slider(
            value = sliderValue,
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { player.seekTo((it * duration).toLong()) }
                scrubPosition = null
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs), style = MaterialTheme.typography.labelSmall)
            Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = player::toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.shuffleEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            IconButton(onClick = player::previous) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = player::playPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = player::next) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next", Modifier.size(36.dp))
            }
            IconButton(onClick = player::cycleRepeat) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Filled.RepeatOne
                    } else {
                        Icons.Filled.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueList(
    state: PlayerUiState,
    player: PlayerConnection,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxWidth()) {
        itemsIndexed(state.queue, key = { _, entry -> "${entry.index}:${entry.title}" }) { index, entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { player.skipToQueueItem(index) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(entry.artworkUrl, Modifier.size(40.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        entry.title,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (index == state.currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Unspecified
                        }
                    )
                    Text(entry.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { player.removeFromQueue(index) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove from queue")
                }
            }
            HorizontalDivider()
        }
    }
}
