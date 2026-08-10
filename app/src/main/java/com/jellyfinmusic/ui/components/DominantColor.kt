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
                    val palette = Palette.from(bmp).generate()
                    // Vibrant first: dark artwork (a video still, a black cover)
                    // yields a near-black muted swatch, which washes the whole
                    // screen out to the same flat background it sits on.
                    palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                }
            }.getOrNull()
        }
        color.value = extracted?.let { Color(it).forBackdrop() } ?: AppColors.SurfaceVariant
    }

    return color
}

/**
 * Keeps an extracted colour usable as a backdrop: dark ones are lifted so the
 * gradient reads as colour rather than black, bright ones are pulled down so
 * white text stays legible over them.
 */
private fun Color.forBackdrop(): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return when {
        luminance < 0.18f -> lerpTo(Color.White, 0.28f)
        luminance > 0.65f -> lerpTo(Color.Black, 0.35f)
        else -> this
    }
}

private fun Color.lerpTo(other: Color, amount: Float) = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
    alpha = 1f
)
