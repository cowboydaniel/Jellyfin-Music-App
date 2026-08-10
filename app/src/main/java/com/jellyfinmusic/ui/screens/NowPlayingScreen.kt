package com.jellyfinmusic.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.jellyfinmusic.playback.PlayerConnection
import com.jellyfinmusic.playback.PlayerUiState
import com.jellyfinmusic.ui.components.Artwork
import com.jellyfinmusic.ui.components.formatDuration
import com.jellyfinmusic.ui.components.rememberDominantColor
import com.jellyfinmusic.ui.theme.AppColors

private enum class PlayerTab(val label: String) { UP_NEXT("UP NEXT"), RELATED("RELATED") }

@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    player: PlayerConnection,
    onCollapse: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(PlayerTab.UP_NEXT) }
    var showQueue by remember { mutableStateOf(false) }
    // While dragging, the slider follows the finger; otherwise the position
    // ticks would fight the gesture.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    val accent by animateColorAsState(
        targetValue = rememberDominantColor(state.artworkUrl).value,
        label = "playerBackground"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(accent, AppColors.Background, AppColors.Background)
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 18f) onCollapse()
                }
            }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCollapse)
                        .padding(8.dp)
                )
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PLAYING FROM",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Secondary
                    )
                    Text(
                        state.album.ifBlank { "Your library" },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) {
                        "Remove from Liked songs"
                    } else {
                        "Save to Liked songs"
                    },
                    tint = if (isFavorite) AppColors.Accent else AppColors.OnBackground,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleFavorite)
                        .padding(8.dp)
                )
            }

            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (showQueue) {
                    QueuePanel(state, player, tab) { tab = it }
                } else {
                    Artwork(
                        state.artworkUrl,
                        Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Text(
                state.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                state.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Secondary,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
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
                colors = SliderDefaults.colors(
                    thumbColor = AppColors.OnBackground,
                    activeTrackColor = AppColors.OnBackground,
                    inactiveTrackColor = AppColors.SurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(
                    formatDuration(state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Secondary
                )
                Text(
                    formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Secondary
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlIcon(
                    Icons.Filled.Shuffle,
                    "Shuffle",
                    active = state.shuffleEnabled,
                    onClick = player::toggleShuffle
                )
                ControlIcon(Icons.Filled.SkipPrevious, "Previous", size = 44, onClick = player::previous)

                // The play button is a solid white disc, the one high-contrast
                // element in the whole player.
                Box(
                    Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(AppColors.OnBackground)
                        .clickable(onClick = player::playPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = AppColors.Background,
                        modifier = Modifier.size(34.dp)
                    )
                }

                ControlIcon(Icons.Filled.SkipNext, "Next", size = 44, onClick = player::next)
                ControlIcon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                    else Icons.Filled.Repeat,
                    "Repeat",
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = player::cycleRepeat
                )
            }

            // Tab strip doubles as the queue toggle, mirroring the way the
            // YouTube Music player swaps artwork for the up-next list.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                PlayerTab.entries.forEach { entry ->
                    val selected = showQueue && tab == entry
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) AppColors.OnBackground else AppColors.Secondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                if (showQueue && tab == entry) {
                                    showQueue = false
                                } else {
                                    tab = entry
                                    showQueue = true
                                }
                            }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    size: Int = 34
) {
    Icon(
        icon,
        contentDescription = description,
        tint = if (active) AppColors.OnBackground else AppColors.Secondary,
        modifier = Modifier
            .size((size + 16).dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
    )
}

@Composable
private fun QueuePanel(
    state: PlayerUiState,
    player: PlayerConnection,
    tab: PlayerTab,
    onTabChange: (PlayerTab) -> Unit
) {
    if (tab == PlayerTab.RELATED) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "Use Radio on an album or artist\nto build a related queue.",
                color = AppColors.Secondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(state.queue, key = { i, entry -> "$i-${entry.title}" }) { index, entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { player.skipToQueueItem(index) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(entry.artworkUrl, Modifier.size(44.dp), shape = RoundedCornerShape(4.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        entry.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == state.currentIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == state.currentIndex) AppColors.OnBackground else Color.White
                    )
                    Text(
                        entry.artist,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Secondary
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = AppColors.Secondary,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { player.removeFromQueue(index) }
                        .padding(6.dp)
                )
            }
        }
    }
}
