package com.jellyfinmusic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.jellyfinmusic.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a muted colour from the artwork so the now-playing screen can wash
 * the background in the album's own palette, the way YouTube Music does.
 * Falls back to the flat surface colour whenever extraction is not possible.
 */
@Composable
fun rememberDominantColor(artworkUrl: String?): State<Color> {
    val context = LocalContext.current
    val color = remember { mutableStateOf(AppColors.SurfaceVariant) }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl == null) {
            color.value = AppColors.SurfaceVariant
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    // Palette needs to read pixels back, which a hardware bitmap forbids.
                    .allowHardware(false)
                    .size(200)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                val bitmap = (result?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bitmap?.let { bmp ->
                    val palette = Palette.from(bmp).clearFilters().generate()
                    palette.darkMutedSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                }
            }.getOrNull()
        }
        color.value = extracted?.let { Color(it) } ?: AppColors.SurfaceVariant
    }

    return color
}
