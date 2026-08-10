package com.jellyfinmusic.data.imports

import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.BaseItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches imported lines against the Jellyfin library.
 *
 * Exported playlists are messy: YouTube titles carry "(Official Music Video)",
 * Spotify separates artists into a list, M3U files may only have a file path.
 * Everything is reduced to a comparable form before scoring, and a match is
 * only claimed when the title agrees — a shared artist alone is never enough.
 */
@Singleton
class TrackMatcher @Inject constructor(
    private val repo: JellyfinRepository
) {

    suspend fun match(track: ImportTrack): MatchedTrack {
        val candidates = gatherCandidates(track)
        if (candidates.isEmpty()) {
            return MatchedTrack(track, null, MatchQuality.NONE, include = false)
        }

        val wantedTitle = track.title.normalize()
        val wantedArtist = track.artist.normalize()

        val scored = candidates.map { candidate ->
            candidate to score(candidate, wantedTitle, wantedArtist, track.album.normalize())
        }.sortedByDescending { it.second }

        val (best, bestScore) = scored.first()
        val quality = when {
            bestScore >= 0.95 -> MatchQuality.EXACT
            bestScore >= 0.72 -> MatchQuality.CLOSE
            bestScore >= 0.5 -> MatchQuality.WEAK
            else -> MatchQuality.NONE
        }
        return MatchedTrack(
            source = track,
            match = best.takeIf { quality != MatchQuality.NONE },
            quality = quality,
            // Weak matches are surfaced but left off, so an import cannot
            // quietly fill a playlist with near-misses.
            include = quality == MatchQuality.EXACT || quality == MatchQuality.CLOSE
        )
    }

    /**
     * Searching on the title alone casts the widest net the server allows;
     * a second pass on "artist title" catches tracks whose titles are too
     * generic to rank on their own.
     */
    private suspend fun gatherCandidates(track: ImportTrack): List<BaseItem> {
        val byTitle = runCatching { repo.search(track.title, "Audio", limit = 25) }
            .getOrDefault(emptyList())
        if (byTitle.isNotEmpty() || track.artist.isBlank()) return byTitle
        return runCatching { repo.search("${track.artist} ${track.title}", "Audio", limit = 25) }
            .getOrDefault(emptyList())
    }

    private fun score(
        candidate: BaseItem,
        wantedTitle: String,
        wantedArtist: String,
        wantedAlbum: String
    ): Double {
        val candidateTitle = candidate.name.orEmpty().normalize()
        val titleScore = similarity(candidateTitle, wantedTitle)
        // A title that does not substantially agree is disqualifying, however
        // well the artist lines up.
        if (titleScore < 0.5) return 0.0

        var total = titleScore * 0.7
        if (wantedArtist.isNotBlank()) {
            val candidateArtist = candidate.artistName.orEmpty().normalize()
            total += similarity(candidateArtist, wantedArtist) * 0.22
        } else {
            total += 0.11
        }
        if (wantedAlbum.isNotBlank()) {
            total += similarity(candidate.album.orEmpty().normalize(), wantedAlbum) * 0.08
        } else {
            total += 0.04
        }
        return total.coerceIn(0.0, 1.0)
    }

    /** Token overlap, which tolerates reordering and extra words. */
    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val tokensA = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val shared = tokensA.intersect(tokensB).size.toDouble()
        return shared / maxOf(tokensA.size, tokensB.size)
    }
}

/** Noise that appears in exported titles but never in a library tag. */
private val NOISE = Regex(
    "\\((official|official video|official music video|official audio|lyrics|lyric video|" +
        "audio|visualizer|hd|4k|remastered[^)]*|explicit|clean)\\)|" +
        "\\[(official|official video|official music video|official audio|lyrics|lyric video|" +
        "audio|visualizer|hd|4k|remastered[^\\]]*|explicit|clean)\\]",
    RegexOption.IGNORE_CASE
)

private val APOSTROPHES = setOf('\'', '\u2019', '\u02BC', '`')

private val FEATURING = Regex("\\b(feat|ft|featuring)\\b\\.?.*", RegexOption.IGNORE_CASE)

/**
 * Reduces a title or artist to something comparable: lower case, no bracketed
 * production noise, no featured-artist tail, no punctuation.
 */
fun String.normalize(): String = lowercase()
    .replace(NOISE, " ")
    .replace(FEATURING, " ")
    // Apostrophes are dropped rather than spaced, so "Don't" and "Dont" agree.
    // Sources disagree constantly here, including straight versus curly quotes.
    .filterNot { it in APOSTROPHES }
    .map { if (it.isLetterOrDigit()) it else ' ' }
    .joinToString("")
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ")
