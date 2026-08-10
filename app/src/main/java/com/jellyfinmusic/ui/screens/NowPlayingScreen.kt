package com.jellyfinmusic.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.jellyfinmusic.network.LyricLine
import com.jellyfinmusic.playback.PlayerConnection
import com.jellyfinmusic.playback.PlayerUiState
import com.jellyfinmusic.ui.LyricsState
import com.jellyfinmusic.ui.components.Artwork
import com.jellyfinmusic.ui.components.formatDuration
import com.jellyfinmusic.ui.components.rememberDominantColor
import com.jellyfinmusic.ui.theme.AppColors

private enum class PlayerTab(val label: String) {
    UP_NEXT("UP NEXT"),
    LYRICS("LYRICS"),
    RELATED("RELATED")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    player: PlayerConnection,
    lyrics: LyricsState,
    onCollapse: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShowMenu: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    onLyricsRequested: () -> Unit = {}
) {
    var tab by remember { mutableStateOf(PlayerTab.UP_NEXT) }
    var panelOpen by remember { mutableStateOf(false) }
    // While dragging, the slider follows the finger; otherwise the position
    // ticks would fight the gesture.
    var scrubPosition by remember { mutableStateOf<Float?>(null) }

    val accent by animateColorAsState(
        targetValue = rememberDominantColor(state.artworkUrl).value,
        label = "playerBackground"
    )

    LaunchedEffect(panelOpen, tab, state.currentItemId) {
        if (panelOpen && tab == PlayerTab.LYRICS) onLyricsRequested()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to accent,
                    0.45f to AppColors.Background,
                    1f to AppColors.Background
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 18f) onCollapse()
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // The gradient runs edge to edge behind the system bars, but the
                // controls must not sit under the clock or the nav buttons.
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIcon(Icons.Filled.KeyboardArrowDown, "Collapse", onClick = onCollapse)
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
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
                CircleIcon(Icons.Filled.MoreVert, "More", onClick = onShowMenu)
            }

            Box(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    panelOpen && tab == PlayerTab.UP_NEXT -> QueuePanel(state, player)
                    panelOpen && tab == PlayerTab.LYRICS -> LyricsPanel(lyrics, state.positionMs)
                    panelOpen && tab == PlayerTab.RELATED -> RelatedPanel(onStartRadio)
                    else -> Artwork(
                        state.artworkUrl,
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Title and the quick actions share a row, as they do in YouTube
            // Music, which keeps the controls from drifting down the screen.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        // Long titles scroll rather than truncate, so the whole
                        // name is readable without opening the track menu.
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Text(
                        state.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Secondary,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
                CircleIcon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (isFavorite) "Remove from Liked songs" else "Save to Liked songs",
                    tint = if (isFavorite) AppColors.Accent else AppColors.OnBackground,
                    onClick = onToggleFavorite
                )
                CircleIcon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    "Add to playlist",
                    onClick = onAddToPlaylist
                )
                CircleIcon(Icons.Filled.Radio, "Start radio", onClick = onStartRadio)
            }

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
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), Arrangement.SpaceBetween) {
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
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlIcon(
                    Icons.Filled.Shuffle,
                    "Shuffle",
                    active = state.shuffleEnabled,
                    onClick = player::toggleShuffle
                )
                ControlIcon(Icons.Filled.SkipPrevious, "Previous", size = 42, onClick = player::previous)

                // The play button is a solid white disc, the one high-contrast
                // element in the whole player.
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AppColors.OnBackground)
                        .clickable(onClick = player::playPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = AppColors.Background,
                        modifier = Modifier.size(32.dp)
                    )
                }

                ControlIcon(Icons.Filled.SkipNext, "Next", size = 42, onClick = player::next)
                ControlIcon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne
                    else Icons.Filled.Repeat,
                    "Repeat",
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    onClick = player::cycleRepeat
                )
            }

            // Tab strip doubles as the panel toggle: tapping the open tab
            // returns to the artwork.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
            ) {
                PlayerTab.entries.forEach { entry ->
                    val selected = panelOpen && tab == entry
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            entry.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) AppColors.OnBackground else AppColors.Secondary,
                            modifier = Modifier
                                .clickable {
                                    if (panelOpen && tab == entry) {
                                        panelOpen = false
                                    } else {
                                        tab = entry
                                        panelOpen = true
                                    }
                                }
                                .padding(vertical = 6.dp)
                        )
                        Box(
                            Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(
                                    if (selected) AppColors.OnBackground else Color.Transparent
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = AppColors.OnBackground
) {
    Icon(
        icon,
        contentDescription = description,
        tint = tint,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp)
    )
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    size: Int = 32
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
private fun QueuePanel(state: PlayerUiState, player: PlayerConnection) {
    val listState = rememberLazyListState()
    // Keep the playing track in view as the queue advances.
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex >= 0) {
            runCatching { listState.animateScrollToItem(state.currentIndex) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
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
                        color = if (index == state.currentIndex) AppColors.Accent else AppColors.OnBackground
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

/**
 * Timestamped lyrics scroll and highlight in step with playback; unsynced ones
 * are shown as a plain block.
 */
@Composable
private fun LyricsPanel(lyrics: LyricsState, positionMs: Long) {
    if (lyrics.isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AppColors.OnBackground, strokeWidth = 2.dp)
        }
        return
    }
    if (lyrics.lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "No lyrics for this track.",
                color = AppColors.Secondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val activeIndex = if (lyrics.isSynced) lyrics.lines.activeLineIndex(positionMs) else -1
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            // Bias the active line towards the middle of the panel.
            runCatching { listState.animateScrollToItem(maxOf(0, activeIndex - 2)) }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                line.text.ifBlank { " " },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    !lyrics.isSynced -> AppColors.OnBackground
                    isActive -> AppColors.OnBackground
                    else -> AppColors.Secondary
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            )
        }
    }
}

/** The last line whose start time has passed. */
private fun List<LyricLine>.activeLineIndex(positionMs: Long): Int =
    indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }

@Composable
private fun RelatedPanel(onStartRadio: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Build a queue of similar tracks from your library.",
            color = AppColors.Secondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Row(
            Modifier
                .padding(top = 16.dp)
                .clip(CircleShape)
                .background(AppColors.OnBackground)
                .clickable(onClick = onStartRadio)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Radio,
                contentDescription = null,
                tint = AppColors.Background,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "Start radio",
                color = AppColors.Background,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
