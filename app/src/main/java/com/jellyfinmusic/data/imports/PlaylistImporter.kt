package com.jellyfinmusic.data.imports

import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.network.ApiProvider
import javax.inject.Inject
import javax.inject.Singleton

/** A playlist read from somewhere, before matching. */
data class SourcePlaylist(
    val name: String,
    val tracks: List<ImportTrack>
)

/**
 * Fetches playlists from external services and files, and turns them into
 * Jellyfin playlists.
 *
 * Nothing here needs a user to log in to Spotify or Google: Spotify is read
 * with the client-credentials grant and YouTube with an API key, which is
 * enough for public playlists and avoids an OAuth round trip inside the app.
 */
@Singleton
class PlaylistImporter @Inject constructor(
    private val apis: ApiProvider,
    private val settings: SettingsStore,
    private val repo: JellyfinRepository,
    private val matcher: TrackMatcher
) {

    fun parseFile(name: String, content: String): SourcePlaylist {
        val playlistName = name.substringBeforeLast('.').ifBlank { "Imported playlist" }
        val tracks = when {
            name.endsWith(".csv", ignoreCase = true) -> PlaylistParsers.parseCsv(content)
            name.endsWith(".m3u", ignoreCase = true) ||
                name.endsWith(".m3u8", ignoreCase = true) -> PlaylistParsers.parseM3u(content)
            // Unknown extension: sniff for the M3U marker, else treat as a list.
            content.lineSequence().firstOrNull()?.startsWith("#EXTM3U") == true ->
                PlaylistParsers.parseM3u(content)
            else -> PlaylistParsers.parseText(content)
        }
        return SourcePlaylist(playlistName, tracks)
    }

    fun parseText(content: String) =
        SourcePlaylist("Imported playlist", PlaylistParsers.parseText(content))

    /** Reads a public Spotify playlist, paging until the service stops. */
    suspend fun fetchSpotify(url: String): SourcePlaylist {
        val s = settings.current
        require(s.hasSpotify) { "Add your Spotify client ID and secret in Settings first" }
        val playlistId = extractId(url, "playlist")
            ?: error("That does not look like a Spotify playlist link")

        val credentials = android.util.Base64.encodeToString(
            "${s.spotifyClientId}:${s.spotifyClientSecret}".toByteArray(),
            android.util.Base64.NO_WRAP
        )
        val token = apis.spotifyAuth().token("Basic $credentials").accessToken
        require(token.isNotBlank()) { "Spotify refused those credentials" }
        val bearer = "Bearer $token"

        val name = runCatching { apis.spotify().playlist(bearer, playlistId).name }
            .getOrDefault("Spotify playlist")

        val tracks = mutableListOf<ImportTrack>()
        var offset = 0
        while (true) {
            val page = apis.spotify().playlistTracks(bearer, playlistId, offset = offset)
            page.items.mapNotNull { it.track }.forEach { track ->
                tracks += ImportTrack(
                    title = track.name,
                    artist = track.artists.joinToString(", ") { it.name },
                    album = track.album?.name.orEmpty()
                )
            }
            if (page.next == null || page.items.isEmpty()) break
            offset += page.items.size
        }
        return SourcePlaylist(name.ifBlank { "Spotify playlist" }, tracks)
    }

    /**
     * Reads a public YouTube or YouTube Music playlist. Video titles are the
     * only metadata available, so "Artist - Title" is split out of them and the
     * channel name is used when a title carries no artist.
     */
    suspend fun fetchYouTube(url: String): SourcePlaylist {
        val key = settings.current.youtubeApiKey
        require(key.isNotBlank()) { "Add a YouTube API key in Settings first" }
        val playlistId = extractYouTubeId(url)
            ?: error("That does not look like a YouTube playlist link")

        val name = runCatching {
            apis.youTube().playlist(key, playlistId).items.firstOrNull()?.snippet?.title
        }.getOrNull().orEmpty()

        val tracks = mutableListOf<ImportTrack>()
        var pageToken: String? = null
        do {
            val page = apis.youTube().playlistItems(key, playlistId, pageToken = pageToken)
            page.items.mapNotNull { it.snippet }.forEach { snippet ->
                val (artist, title) = PlaylistParsers.splitArtistTitle(snippet.title)
                val channel = snippet.channelTitle
                    ?.removeSuffix(" - Topic")
                    .orEmpty()
                tracks += ImportTrack(
                    title = title,
                    artist = artist.ifBlank { channel }
                )
            }
            pageToken = page.nextPageToken
        } while (pageToken != null)

        return SourcePlaylist(name.ifBlank { "YouTube playlist" }, tracks)
    }

    /** Matches every line against the library, reporting progress as it goes. */
    suspend fun matchAll(
        tracks: List<ImportTrack>,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<MatchedTrack> {
        val results = mutableListOf<MatchedTrack>()
        tracks.forEachIndexed { index, track ->
            results += matcher.match(track)
            onProgress(index + 1, tracks.size)
        }
        return results
    }

    /** Creates the Jellyfin playlist from whatever the user kept. */
    suspend fun createPlaylist(name: String, matched: List<MatchedTrack>): Int {
        val ids = matched.filter { it.include }.mapNotNull { it.match?.id }
        require(ids.isNotEmpty()) { "Nothing selected to import" }
        repo.createPlaylist(name, ids)
        return ids.size
    }

    private fun extractId(url: String, kind: String): String? {
        val marker = "$kind/"
        val index = url.indexOf(marker)
        if (index < 0) return url.takeIf { it.length in 20..40 && !it.contains('/') }
        return url.substring(index + marker.length)
            .substringBefore('?')
            .substringBefore('/')
            .takeIf { it.isNotBlank() }
    }

    private fun extractYouTubeId(url: String): String? {
        val listParam = Regex("[?&]list=([^&]+)").find(url)?.groupValues?.getOrNull(1)
        if (!listParam.isNullOrBlank()) return listParam
        return url.takeIf { it.startsWith("PL") || it.startsWith("OLAK") || it.startsWith("VL") }
    }
}
