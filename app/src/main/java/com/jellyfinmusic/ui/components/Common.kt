package com.jellyfinmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.jellyfinmusic.ui.theme.AppColors

/** Artwork with a placeholder that keeps the layout stable while loading or on failure. */
@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    isArtist: Boolean = false
) {
    val placeholder: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isArtist) Icons.Filled.Person else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = AppColors.Secondary
            )
        }
    }

    Box(modifier.clip(shape)) {
        if (url == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().background(AppColors.SurfaceVariant)) },
                error = { placeholder() }
            )
        }
    }
}

/**
 * A shelf header: bold title on the left, optional "more" affordance on the
 * right, matching the section headers that separate YouTube Music's carousels.
 */
@Composable
fun ShelfHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onMoreClick != null) Modifier.clickable(onClick = onMoreClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Secondary)
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        if (onMoreClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.Secondary
            )
        }
    }
}

/** Square artwork card used for albums and playlists in horizontal shelves. */
@Composable
fun AlbumCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 150
) {
    Column(
        modifier = modifier
            .width(width.dp)
            .clickable(onClick = onClick)
    ) {
        Artwork(
            artworkUrl,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Circular card for artists, the shape YouTube Music reserves for people. */
@Composable
fun ArtistCard(
    name: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 130
) {
    Column(
        modifier = modifier
            .width(size.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Artwork(
            artworkUrl,
            Modifier.size(size.dp),
            shape = CircleShape,
            isArtist = true
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Dense track row with small artwork and an overflow button. */
@Composable
fun TrackRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    artSize: Int = 48,
    onMenuClick: (() -> Unit)? = null,
    isArtist: Boolean = false,
    artShape: Shape = RoundedCornerShape(4.dp)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(artworkUrl, Modifier.size(artSize.dp), shape = artShape, isArtist = isArtist)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaying) AppColors.Accent else AppColors.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMenuClick != null) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = AppColors.Secondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onMenuClick)
                    .padding(6.dp)
            )
        }
    }
}

/** Pill-shaped filter chip. Selected state inverts to white-on-dark like YT Music. */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppColors.ChipSelected else AppColors.Chip)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, AppColors.SurfaceVariant, RoundedCornerShape(50))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AppColors.Background else AppColors.OnBackground
        )
    }
}

@Composable
fun PillRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            Pill(option, option == selected, { onSelect(option) })
        }
    }
}

@Composable
fun LoadingRow(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = AppColors.OnBackground, strokeWidth = 2.dp)
    }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = AppColors.Secondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StateBox(
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    when {
        isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AppColors.OnBackground, strokeWidth = 2.dp)
        }
        error != null -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp)
            )
        }
        isEmpty -> Box(modifier.fillMaxSize(), Alignment.Center) {
            Text(emptyMessage, color = AppColors.Secondary)
        }
        else -> content()
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
