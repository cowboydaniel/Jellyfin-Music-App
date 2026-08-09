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

@Singleton
class LidarrRepository @Inject constructor(
    private val apis: ApiProvider,
    private val settings: SettingsStore
) {

    suspend fun lookupArtists(term: String): List<LidarrArtist> = apis.lidarr().lookupArtist(term)

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
