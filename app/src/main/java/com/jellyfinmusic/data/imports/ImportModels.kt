package com.jellyfinmusic.data.imports

import com.jellyfinmusic.network.BaseItem

/** One line of an imported playlist, before any matching has happened. */
data class ImportTrack(
    val title: String,
    val artist: String = "",
    val album: String = "",
    /** File path, when the source was an M3U pointing at real files. */
    val path: String? = null
) {
    val display: String
        get() = if (artist.isBlank()) title else "$artist — $title"
}

/** How confident the matcher is that a library track is the right one. */
enum class MatchQuality { EXACT, CLOSE, WEAK, NONE }

data class MatchedTrack(
    val source: ImportTrack,
    val match: BaseItem?,
    val quality: MatchQuality,
    /** Excluded from the playlist when false; weak matches start off. */
    val include: Boolean
)

/** Where an import came from, which decides what the UI has to ask for. */
enum class ImportSource(val label: String) {
    M3U("M3U file"),
    CSV("CSV file"),
    TEXT("Pasted list"),
    SPOTIFY("Spotify playlist"),
    YOUTUBE("YouTube playlist")
}
