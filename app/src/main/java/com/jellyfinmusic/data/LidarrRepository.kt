package com.jellyfinmusic.data

import com.jellyfinmusic.network.AlbumAddOptions
import com.jellyfinmusic.network.ApiProvider
import com.jellyfinmusic.network.ArtistAddOptions
import com.jellyfinmusic.network.LidarrAlbum
import com.jellyfinmusic.network.LidarrArtist
import com.jellyfinmusic.network.LidarrProfile
import com.jellyfinmusic.network.LidarrRootFolder
import javax.inject.Inject
import javax.inject.Singleton

/** A song found on MusicBrainz, carrying the album Lidarr would have to fetch. */
data class SongMatch(
    val recordingId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String,
    val releaseGroupId: String,
    val artworkUrl: String?
)

@Singleton
class LidarrRepository @Inject constructor(
    private val apis: ApiProvider,
    private val settings: SettingsStore
) {

    suspend fun lookupArtists(term: String): List<LidarrArtist> = apis.lidarr().lookupArtist(term)

    /**
     * Song-level search, which Lidarr itself does not offer.
     *
     * Lidarr can only monitor and grab whole albums, so a song is looked up on
     * MusicBrainz and reported together with the release it belongs to. The
     * request that follows is for that album — see [resolveAlbumForSong].
     */
    suspend fun lookupSongs(term: String): List<SongMatch> {
        val recordings = apis.musicBrainz().searchRecordings(term).recordings
        return recordings.mapNotNull { recording ->
            // Prefer a release that belongs to a proper album release-group;
            // singles and compilations are a poor thing to request on your behalf.
            val release = recording.releases.firstOrNull {
                it.releaseGroup?.primaryType.equals("Album", ignoreCase = true)
            } ?: recording.releases.firstOrNull() ?: return@mapNotNull null

            val releaseGroupId = release.releaseGroup?.id ?: return@mapNotNull null
            SongMatch(
                recordingId = recording.id,
                title = recording.title,
                artistName = recording.artistName,
                albumTitle = release.releaseGroup.title ?: release.title,
                releaseGroupId = releaseGroupId,
                // Cover Art Archive serves art keyed by release-group; a miss
                // just 404s and the UI falls back to its placeholder.
                artworkUrl = "https://coverartarchive.org/release-group/$releaseGroupId/front-250"
            )
        }.distinctBy { it.releaseGroupId to it.title.lowercase() }
    }

    /**
     * Finds the Lidarr album record behind a [SongMatch] so it can be added.
     * Matching on the MusicBrainz release-group ID is exact; the text search is
     * only there to give Lidarr something to resolve.
     */
    suspend fun resolveAlbumForSong(song: SongMatch): LidarrAlbum? {
        val candidates = runCatching {
            apis.lidarr().lookupAlbum("lidarr:${song.releaseGroupId}")
        }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: runCatching {
                apis.lidarr().lookupAlbum("${song.artistName} ${song.albumTitle}")
            }.getOrDefault(emptyList())

        return candidates.firstOrNull { it.foreignAlbumId == song.releaseGroupId }
            ?: candidates.firstOrNull {
                it.title.equals(song.albumTitle, ignoreCase = true)
            }
            ?: candidates.firstOrNull()
    }

    suspend fun lookupAlbums(term: String): List<LidarrAlbum> = apis.lidarr().lookupAlbum(term)

    suspend fun rootFolders(): List<LidarrRootFolder> = apis.lidarr().rootFolders()

    suspend fun qualityProfiles(): List<LidarrProfile> = apis.lidarr().qualityProfiles()

    suspend fun metadataProfiles(): List<LidarrProfile> = apis.lidarr().metadataProfiles()

    /**
     * Adds an artist monitored, with a search kicked off immediately. Lidarr's
     * lookup results are already shaped like the POST body, so the only fields
     * that need filling in are the ones the server can't infer.
     */
    suspend fun addArtist(artist: LidarrArtist): Result<LidarrArtist> = runCatching {
        val s = settings.current
        val rootFolder = s.lidarrRootFolder.ifBlank {
            rootFolders().firstOrNull()?.path
                ?: error("No root folder configured in Lidarr")
        }
        apis.lidarr().addArtist(
            artist.copy(
                id = null,
                monitored = true,
                rootFolderPath = rootFolder,
                qualityProfileId = s.lidarrQualityProfileId,
                metadataProfileId = s.lidarrMetadataProfileId,
                addOptions = ArtistAddOptions(monitor = "all", searchForMissingAlbums = true)
            )
        )
    }

    /**
     * Adding an album requires its artist to exist in Lidarr, so the artist is
     * submitted first (unmonitored albums) and the album is then monitored and searched.
     */
    suspend fun addAlbum(album: LidarrAlbum): Result<LidarrAlbum> = runCatching {
        val s = settings.current
        val rootFolder = s.lidarrRootFolder.ifBlank {
            rootFolders().firstOrNull()?.path
                ?: error("No root folder configured in Lidarr")
        }
        val artist = album.artist ?: error("Lidarr result has no artist attached")

        val preparedArtist = artist.copy(
            id = null,
            monitored = true,
            rootFolderPath = rootFolder,
            qualityProfileId = s.lidarrQualityProfileId,
            metadataProfileId = s.lidarrMetadataProfileId,
            addOptions = ArtistAddOptions(monitor = "none", searchForMissingAlbums = false)
        )

        apis.lidarr().addAlbum(
            album.copy(
                id = null,
                monitored = true,
                artist = preparedArtist,
                addOptions = AlbumAddOptions(searchForNewAlbum = true)
            )
        )
    }
}
