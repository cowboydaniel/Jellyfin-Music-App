package com.jellyfinmusic.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Reads public Spotify playlists.
 *
 * Uses the client-credentials grant, which authenticates the application rather
 * than a person — so no Spotify login is needed, at the cost of only being able
 * to read public playlists. The user supplies a client ID and secret from a free
 * Spotify developer app.
 */
interface SpotifyAuthApi {
    @FormUrlEncoded
    @POST("api/token")
    suspend fun token(
        @Header("Authorization") basicAuth: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): SpotifyToken
}

interface SpotifyApi {
    @GET("v1/playlists/{playlistId}/tracks")
    suspend fun playlistTracks(
        @Header("Authorization") bearer: String,
        @Path("playlistId") playlistId: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("fields") fields: String =
            "items(track(name,artists(name),album(name))),next"
    ): SpotifyTracksPage

    @GET("v1/playlists/{playlistId}")
    suspend fun playlist(
        @Header("Authorization") bearer: String,
        @Path("playlistId") playlistId: String,
        @Query("fields") fields: String = "name"
    ): SpotifyPlaylist
}

@Serializable
data class SpotifyToken(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("expires_in") val expiresIn: Int = 0
)

@Serializable
data class SpotifyPlaylist(
    @SerialName("name") val name: String = ""
)

@Serializable
data class SpotifyTracksPage(
    @SerialName("items") val items: List<SpotifyItem> = emptyList(),
    @SerialName("next") val next: String? = null
)

@Serializable
data class SpotifyItem(
    @SerialName("track") val track: SpotifyTrack? = null
)

@Serializable
data class SpotifyTrack(
    @SerialName("name") val name: String = "",
    @SerialName("artists") val artists: List<SpotifyArtist> = emptyList(),
    @SerialName("album") val album: SpotifyAlbum? = null
)

@Serializable
data class SpotifyArtist(@SerialName("name") val name: String = "")

@Serializable
data class SpotifyAlbum(@SerialName("name") val name: String = "")

/**
 * Reads public YouTube and YouTube Music playlists through the Data API, which
 * needs only an API key rather than an OAuth flow.
 */
interface YouTubeApi {
    @GET("youtube/v3/playlistItems")
    suspend fun playlistItems(
        @Query("key") key: String,
        @Query("playlistId") playlistId: String,
        @Query("part") part: String = "snippet",
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): YouTubePage

    @GET("youtube/v3/playlists")
    suspend fun playlist(
        @Query("key") key: String,
        @Query("id") id: String,
        @Query("part") part: String = "snippet"
    ): YouTubePlaylistPage
}

@Serializable
data class YouTubePage(
    @SerialName("items") val items: List<YouTubeItem> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null
)

@Serializable
data class YouTubeItem(
    @SerialName("snippet") val snippet: YouTubeSnippet? = null
)

@Serializable
data class YouTubeSnippet(
    @SerialName("title") val title: String = "",
    @SerialName("videoOwnerChannelTitle") val channelTitle: String? = null
)

@Serializable
data class YouTubePlaylistPage(
    @SerialName("items") val items: List<YouTubeItem> = emptyList()
)
