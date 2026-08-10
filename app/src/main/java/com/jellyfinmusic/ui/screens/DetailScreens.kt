package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.ui.DetailViewModel
import com.jellyfinmusic.ui.components.AlbumCard
import com.jellyfinmusic.ui.components.Artwork
import com.jellyfinmusic.ui.components.ShelfHeader
import com.jellyfinmusic.ui.components.StateBox
import com.jellyfinmusic.ui.components.TrackRow
import com.jellyfinmusic.ui.components.formatDuration
import com.jellyfinmusic.ui.theme.AppColors

@Composable
fun AlbumDetailScreen(
    albumId: String,
    isPlaylist: Boolean,
    fallbackTitle: String,
    contentPadding: PaddingValues,
    onDeleted: () -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(albumId, isPlaylist) { viewModel.loadAlbum(albumId, isPlaylist) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = AppColors.Surface,
            title = { Text("Delete playlist?") },
            text = { Text("\"${'$'}{state.header?.name ?: fallbackTitle}\" will be removed from your server.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteCurrentPlaylist(onDeleted)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    StateBox(state.isLoading, state.error, false, "") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                val header = state.header
                DetailHeader(
                    title = header?.name ?: fallbackTitle,
                    subtitle = listOfNotNull(
                        if (isPlaylist) "Playlist" else "Album",
                        header?.artistName,
                        header?.productionYear?.toString(),
                        "${state.tracks.size} tracks"
                    ).joinToString(" · "),
                    artworkUrl = header?.let { viewModel.imageUrl(it) },
                    // Playlists rarely have their own art, so a mosaic of the
                    // first few tracks stands in, as YouTube Music does.
                    mosaicUrls = if (isPlaylist && header?.imageTags?.get("Primary") == null) {
                        state.tracks.take(4).mapNotNull { viewModel.imageUrl(it) }
                    } else {
                        emptyList()
                    },
                    onPlay = { viewModel.playAll(shuffle = false) },
                    onShuffle = { viewModel.playAll(shuffle = true) },
                    onRadio = viewModel::startRadio,
                    onDownload = viewModel::downloadAll,
                    isFavorite = header != null && header.id in favorites,
                    onToggleFavorite = { header?.let(viewModel::toggleFavorite) },
                    onDelete = if (isPlaylist) ({ confirmDelete = true }) else null
                )
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
                    Text(
                        "No tracks here",
                        color = AppColors.Secondary,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artistId: String,
    fallbackName: String,
    contentPadding: PaddingValues,
    onAlbumClick: (BaseItem) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    LaunchedEffect(artistId) { viewModel.loadArtist(artistId) }

    StateBox(state.isLoading, state.error, false, "") {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
        ) {
            item {
                ArtistHeader(
                    name = state.header?.name ?: fallbackName,
                    artworkUrl = state.header?.let { viewModel.imageUrl(it) },
                    albumCount = state.albums.size,
                    isFavorite = state.header != null && state.header!!.id in favorites,
                    onToggleFavorite = { state.header?.let(viewModel::toggleFavorite) },
                    onShuffle = { viewModel.playAll(shuffle = true) },
                    onRadio = viewModel::startRadio
                )
            }

            if (state.albums.isNotEmpty()) {
                item { ShelfHeader("Albums") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.albums, key = { it.id }) { album ->
                            AlbumCard(
                                title = album.name.orEmpty(),
                                subtitle = album.productionYear?.toString(),
                                artworkUrl = viewModel.imageUrl(album),
                                onClick = { onAlbumClick(album) }
                            )
                        }
                    }
                }
            }

            if (state.tracks.isNotEmpty()) {
                item { ShelfHeader("Songs") }
                itemsIndexed(state.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        title = track.name.orEmpty(),
                        subtitle = listOfNotNull(
                            track.album,
                            formatDuration(track.durationMs)
                        ).joinToString(" · "),
                        artworkUrl = viewModel.imageUrl(track),
                        onClick = { viewModel.play(index) },
                        onMenuClick = { viewModel.showMenu(track) },
                        isFavorite = track.id in favorites,
                        onFavoriteClick = { viewModel.toggleFavorite(track) }
                    )
                }
            }
        }
    }
}

/**
 * Large centred artwork over a soft gradient, with the primary actions directly
 * beneath it — the layout YouTube Music uses at the top of every album page.
 */
@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRadio: () -> Unit,
    onDownload: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null,
    mosaicUrls: List<String> = emptyList()
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(AppColors.SurfaceVariant, AppColors.Background)
                )
            )
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (mosaicUrls.size >= 4) {
            Column(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.6f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                listOf(mosaicUrls.take(2), mosaicUrls.drop(2).take(2)).forEach { row ->
                    Row(Modifier.weight(1f)) {
                        row.forEach { url ->
                            Artwork(
                                url,
                                Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(0.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Artwork(
                artworkUrl,
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(0.6f)
                    .aspectRatio(1f),
                shape = RoundedCornerShape(10.dp)
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Row(
            Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundAction(
                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isFavorite) "Remove from library" else "Save to library",
                onToggleFavorite,
                tint = if (isFavorite) AppColors.Accent else AppColors.OnBackground
            )
            RoundAction(Icons.Filled.Shuffle, "Shuffle", onShuffle)
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.OnBackground)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = AppColors.Background,
                    modifier = Modifier.size(34.dp)
                )
            }
            RoundAction(Icons.Filled.Download, "Download", onDownload)
            onDelete?.let { RoundAction(Icons.Filled.Delete, "Delete playlist", it) }
        }
    }
}

@Composable
private fun ArtistHeader(
    name: String,
    artworkUrl: String?,
    albumCount: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRadio: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        Artwork(
            artworkUrl,
            Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            isArtist = true
        )
        // Scrim so white type stays readable over any artist photo.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, AppColors.Background.copy(alpha = 0.95f))
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(name, style = MaterialTheme.typography.headlineMedium)
            Text(
                "$albumCount album${if (albumCount == 1) "" else "s"} in your library",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onShuffle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.OnBackground,
                        contentColor = AppColors.Background
                    )
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, Modifier.size(20.dp))
                    Text("Shuffle", Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onRadio) {
                    Icon(Icons.Filled.Radio, contentDescription = null, Modifier.size(18.dp))
                    Text("Radio", Modifier.padding(start = 6.dp))
                }
                RoundAction(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (isFavorite) "Remove from library" else "Save to library",
                    onToggleFavorite,
                    tint = if (isFavorite) AppColors.Accent else AppColors.OnBackground
                )
            }
        }
    }
}

/** Circular secondary action used around the header's play button. */
@Composable
private fun RoundAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = AppColors.OnBackground
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
