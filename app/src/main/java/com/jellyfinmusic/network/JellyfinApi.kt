package com.jellyfinmusic.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyfinApi {

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(@Body body: AuthenticateRequest): AuthenticationResult

    /**
     * The general-purpose item query. Callers select what they want with
     * [includeItemTypes] plus either [parentId] (children of an album) or
     * [artistIds] (albums belonging to an artist).
     */
    @GET("Items")
    suspend fun getItems(
        @Query("userId") userId: String,
        @Query("includeItemTypes") includeItemTypes: String? = null,
        @Query("parentId") parentId: String? = null,
        @Query("artistIds") artistIds: String? = null,
        @Query("albumArtistIds") albumArtistIds: String? = null,
        @Query("searchTerm") searchTerm: String? = null,
        @Query("recursive") recursive: Boolean = true,
        @Query("sortBy") sortBy: String? = null,
        @Query("sortOrder") sortOrder: String? = "Ascending",
        @Query("genres") genres: String? = null,
        @Query("ids") ids: String? = null,
        @Query("filters") filters: String? = null,
        @Query("startIndex") startIndex: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("fields") fields: String = "PrimaryImageAspectRatio,ChildCount,Genres,UserData"
    ): ItemsResponse

    @GET("Playlists/{playlistId}/Items")
    suspend fun getPlaylistItems(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String,
        @Query("fields") fields: String = "PrimaryImageAspectRatio,UserData"
    ): ItemsResponse

    @POST("Playlists")
    suspend fun createPlaylist(@Body body: CreatePlaylistRequest): CreatePlaylistResponse

    @POST("Playlists/{playlistId}/Items")
    suspend fun addToPlaylist(
        @Path("playlistId") playlistId: String,
        @Query("ids") ids: String,
        @Query("userId") userId: String
    )

    /** Removal is by PlaylistItemId, not track ID, so duplicates stay distinct. */
    @DELETE("Playlists/{playlistId}/Items")
    suspend fun removeFromPlaylist(
        @Path("playlistId") playlistId: String,
        @Query("entryIds") entryIds: String
    )

    @POST("Playlists/{playlistId}/Items/{itemId}/Move/{newIndex}")
    suspend fun movePlaylistItem(
        @Path("playlistId") playlistId: String,
        @Path("itemId") playlistItemId: String,
        @Path("newIndex") newIndex: Int
    )

    @DELETE("Items/{itemId}")
    suspend fun deleteItem(@Path("itemId") itemId: String)

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun markFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    )

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun unmarkFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    )

    /**
     * Playback reporting. Without these the server never increments play counts
     * or records a last-played date, so anything ranked on listening history
     * stays empty no matter how much is played.
     */
    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(@Body body: PlaybackReport)

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(@Body body: PlaybackReport)

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(@Body body: PlaybackReport)

    /**
     * Track lyrics, plain or timestamped. Added in Jellyfin 10.9; older servers
     * return 404, which the UI treats as "no lyrics".
     */
    @GET("Audio/{itemId}/Lyrics")
    suspend fun getLyrics(@Path("itemId") itemId: String): LyricsResponse

    /** Music genres present in the library, used for the Explore mood chips. */
    @GET("MusicGenres")
    suspend fun getMusicGenres(
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 40
    ): ItemsResponse

    /**
     * Server-generated radio seeded from any item — the closest Jellyfin
     * equivalent of "start radio" on a song or album.
     */
    @GET("Items/{itemId}/InstantMix")
    suspend fun getInstantMix(
        @Path("itemId") itemId: String,
        @Query("userId") userId: String,
        @Query("limit") limit: Int = 100
    ): ItemsResponse
}
