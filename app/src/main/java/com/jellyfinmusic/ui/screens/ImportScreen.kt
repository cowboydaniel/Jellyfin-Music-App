package com.jellyfinmusic.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.data.imports.ImportSource
import com.jellyfinmusic.data.imports.MatchQuality
import com.jellyfinmusic.data.imports.MatchedTrack
import com.jellyfinmusic.ui.ImportViewModel
import com.jellyfinmusic.ui.components.Pill
import com.jellyfinmusic.ui.label
import com.jellyfinmusic.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import a playlist from a file, a pasted list, Spotify or YouTube, match it
 * against the library, and request whatever is missing from Lidarr.
 */
@Composable
fun ImportScreen(
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refreshCapabilities() }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val index = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        )
                        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                    }
                } ?: "Imported playlist"
                val content = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().decodeToString()
                        }
                    }.getOrNull()
                }
                if (content != null) viewModel.importFile(name, content)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        if (state.matched.isEmpty()) {
            item {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Bring a playlist across from anywhere. Tracks you already have are " +
                            "matched automatically; anything missing can go straight to Lidarr.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Secondary
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImportSource.entries.take(3).forEach { source ->
                            Pill(
                                text = source.label,
                                selected = state.source == source,
                                onClick = { viewModel.setSource(source) }
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImportSource.entries.drop(3).forEach { source ->
                            Pill(
                                text = source.label,
                                selected = state.source == source,
                                onClick = { viewModel.setSource(source) }
                            )
                        }
                    }

                    when (state.source) {
                        ImportSource.M3U, ImportSource.CSV -> {
                            Text(
                                if (state.source == ImportSource.M3U) {
                                    "Pick an .m3u or .m3u8 file exported from any player."
                                } else {
                                    "Pick a .csv exported from Exportify, TuneMyMusic or similar."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Secondary
                            )
                            Button(
                                onClick = { pickFile.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.OnBackground,
                                    contentColor = AppColors.Background
                                ),
                                shape = RoundedCornerShape(50)
                            ) { Text("Choose file") }
                        }

                        ImportSource.TEXT -> {
                            TextField(
                                value = state.pastedText,
                                onValueChange = viewModel::setPastedText,
                                placeholder = {
                                    Text("Artist - Title, one per line", color = AppColors.Secondary)
                                },
                                colors = darkFieldColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                            Button(
                                onClick = viewModel::importPastedText,
                                enabled = state.pastedText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.OnBackground,
                                    contentColor = AppColors.Background
                                ),
                                shape = RoundedCornerShape(50)
                            ) { Text("Match these tracks") }
                        }

                        ImportSource.SPOTIFY, ImportSource.YOUTUBE -> {
                            val configured = if (state.source == ImportSource.SPOTIFY) {
                                state.hasSpotify
                            } else {
                                state.hasYouTube
                            }
                            Text(
                                if (state.source == ImportSource.SPOTIFY) {
                                    "Paste a public Spotify playlist link. Needs a Spotify " +
                                        "client ID and secret in Settings — free, and no " +
                                        "Spotify login required."
                                } else {
                                    "Paste a public YouTube or YouTube Music playlist link. " +
                                        "Needs a YouTube API key in Settings."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.Secondary
                            )
                            TextField(
                                value = state.url,
                                onValueChange = viewModel::setUrl,
                                singleLine = true,
                                placeholder = { Text("Playlist link", color = AppColors.Secondary) },
                                colors = darkFieldColors(),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = viewModel::importFromUrl,
                                enabled = configured && state.url.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.OnBackground,
                                    contentColor = AppColors.Background
                                ),
                                shape = RoundedCornerShape(50)
                            ) { Text(if (configured) "Fetch playlist" else "Not configured") }
                        }
                    }
                }
            }
        }

        state.progress?.let { (done, total) ->
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Matching $done of $total…",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Secondary
                    )
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        state.error?.let {
            item {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
        state.status?.let {
            item {
                Text(
                    it,
                    color = AppColors.Accent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        if (state.matched.isNotEmpty()) {
            item {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextField(
                        value = state.playlistName,
                        onValueChange = viewModel::setPlaylistName,
                        singleLine = true,
                        label = { Text("Playlist name", color = AppColors.Secondary) },
                        colors = darkFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = viewModel::createPlaylist,
                            enabled = state.selectedCount > 0 && !state.isWorking,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.OnBackground,
                                contentColor = AppColors.Background
                            ),
                            shape = RoundedCornerShape(50)
                        ) { Text("Create with ${state.selectedCount}") }
                        OutlinedButton(
                            onClick = { viewModel.selectAllFound(true) },
                            shape = RoundedCornerShape(50)
                        ) { Text("Select all") }
                        OutlinedButton(
                            onClick = viewModel::reset,
                            shape = RoundedCornerShape(50)
                        ) { Text("Start over") }
                    }
                    if (state.finished) {
                        Button(
                            onClick = onDone,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.SurfaceVariant,
                                contentColor = AppColors.OnBackground
                            )
                        ) { Text("Done") }
                    }
                }
            }

            if (state.missing.isNotEmpty() && state.hasLidarr) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${state.missing.size} not in your library",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = viewModel::requestAllMissing,
                            shape = RoundedCornerShape(50)
                        ) { Text("Request all") }
                    }
                }
            }

            item { HorizontalDivider(color = AppColors.SurfaceVariant) }

            itemsIndexed(state.matched) { index, track ->
                MatchRow(
                    track = track,
                    requested = track.source.display in state.requestedKeys,
                    canRequest = state.hasLidarr,
                    onToggle = { viewModel.toggle(index) },
                    onRequest = { viewModel.requestMissing(track) }
                )
            }
        }

        if (state.isWorking && state.progress == null) {
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
    }
}

@Composable
private fun MatchRow(
    track: MatchedTrack,
    requested: Boolean,
    canRequest: Boolean,
    onToggle: () -> Unit,
    onRequest: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (track.match != null) {
            Checkbox(checked = track.include, onCheckedChange = { onToggle() })
        } else {
            Box(Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.match?.name ?: track.source.title,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                track.source.display,
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary
            )
        }
        QualityChip(track.quality)
        if (track.match == null && canRequest) {
            Box(Modifier.padding(start = 6.dp)) {
                if (requested) {
                    Icon(
                        Icons.Filled.HourglassTop,
                        contentDescription = "Requested",
                        tint = AppColors.Accent,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Request from Lidarr",
                        tint = AppColors.OnBackground,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AppColors.SurfaceVariant)
                            .clickable(onClick = onRequest)
                            .padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityChip(quality: MatchQuality) {
    val (background, foreground) = when (quality) {
        MatchQuality.EXACT -> AppColors.SurfaceVariant to AppColors.OnBackground
        MatchQuality.CLOSE -> AppColors.SurfaceVariant to AppColors.Secondary
        MatchQuality.WEAK -> AppColors.SurfaceVariant to AppColors.Accent
        MatchQuality.NONE -> Color.Transparent to AppColors.Secondary
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (quality == MatchQuality.EXACT) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(12.dp)
            )
        }
        Text(
            quality.label(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = foreground,
            modifier = Modifier.padding(start = if (quality == MatchQuality.EXACT) 4.dp else 0.dp)
        )
    }
}

@Composable
private fun darkFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = AppColors.SurfaceVariant,
    unfocusedContainerColor = AppColors.SurfaceVariant,
    focusedIndicatorColor = AppColors.Accent,
    unfocusedIndicatorColor = Color.Transparent
)
