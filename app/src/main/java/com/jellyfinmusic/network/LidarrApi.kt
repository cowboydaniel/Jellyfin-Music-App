package com.jellyfinmusic.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LidarrApi {

    @GET("api/v1/artist/lookup")
    suspend fun lookupArtist(@Query("term") term: String): List<LidarrArtist>

    @GET("api/v1/album/lookup")
    suspend fun lookupAlbum(@Query("term") term: String): List<LidarrAlbum>

    @GET("api/v1/rootfolder")
    suspend fun rootFolders(): List<LidarrRootFolder>

    @GET("api/v1/qualityprofile")
    suspend fun qualityProfiles(): List<LidarrProfile>

    @GET("api/v1/metadataprofile")
    suspend fun metadataProfiles(): List<LidarrProfile>

    @POST("api/v1/artist")
    suspend fun addArtist(@Body artist: LidarrArtist): LidarrArtist

    @POST("api/v1/album")
    suspend fun addAlbum(@Body album: LidarrAlbum): LidarrAlbum
}

@Serializable
data class LidarrArtist(
    @SerialName("id") val id: Int? = null,
    @SerialName("artistName") val artistName: String = "",
    @SerialName("foreignArtistId") val foreignArtistId: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("disambiguation") val disambiguation: String? = null,
    @SerialName("images") val images: List<LidarrImage> = emptyList(),
    @SerialName("monitored") val monitored: Boolean = true,
    @SerialName("qualityProfileId") val qualityProfileId: Int? = null,
    @SerialName("metadataProfileId") val metadataProfileId: Int? = null,
    @SerialName("rootFolderPath") val rootFolderPath: String? = null,
    @SerialName("addOptions") val addOptions: ArtistAddOptions? = null
)

@Serializable
data class LidarrAlbum(
    @SerialName("id") val id: Int? = null,
    @SerialName("title") val title: String = "",
    @SerialName("foreignAlbumId") val foreignAlbumId: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("albumType") val albumType: String? = null,
    @SerialName("images") val images: List<LidarrImage> = emptyList(),
    @SerialName("monitored") val monitored: Boolean = true,
    @SerialName("artist") val artist: LidarrArtist? = null,
    @SerialName("addOptions") val addOptions: AlbumAddOptions? = null
)

@Serializable
data class LidarrImage(
    @SerialName("coverType") val coverType: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("remoteUrl") val remoteUrl: String? = null
)

@Serializable
data class LidarrRootFolder(
    @SerialName("id") val id: Int = 0,
    @SerialName("path") val path: String = ""
)

@Serializable
data class LidarrProfile(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = ""
)

/** Tells Lidarr to kick off a search as soon as the artist is added. */
@Serializable
data class ArtistAddOptions(
    @SerialName("monitor") val monitor: String = "all",
    @SerialName("searchForMissingAlbums") val searchForMissingAlbums: Boolean = true
)

@Serializable
data class AlbumAddOptions(
    @SerialName("searchForNewAlbum") val searchForNewAlbum: Boolean = true
)
