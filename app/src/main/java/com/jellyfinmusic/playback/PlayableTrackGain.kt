package com.jellyfinmusic.playback

import kotlin.math.pow

/**
 * Volume normalisation.
 *
 * Jellyfin reports each track's integrated loudness in LUFS. Comparing that to
 * a reference level gives a per-track gain, which is applied as a player volume
 * multiplier — quiet masters come up, loud ones come down, so a library mixing
 * video rips with studio releases stops lurching between tracks.
 */
object Normalization {

    /** Broadcast-style reference; roughly where most streaming services sit. */
    private const val TARGET_LUFS = -18.0

    /** Kept modest so a badly tagged track cannot blow the volume out. */
    private const val MAX_GAIN_DB = 6.0
    private const val MIN_GAIN_DB = -12.0

    /** Multiplier for [android.media.MediaPlayer]-style linear volume, 0..1. */
    fun volumeFor(lufs: Double?, enabled: Boolean): Float {
        if (!enabled || lufs == null || lufs == 0.0) return 1f
        val gainDb = (TARGET_LUFS - lufs).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        // Positive gain cannot be applied by attenuation alone, so anything
        // above reference is left at full volume rather than clipped upward.
        if (gainDb >= 0) return 1f
        return 10.0.pow(gainDb / 20.0).toFloat().coerceIn(0.1f, 1f)
    }
}
