package com.jellyfinmusic.network

import retrofit2.http.Body
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
        @Query("fields") fields: String = "PrimaryImageAspectRatio,ChildCount,Genres"
    ): ItemsResponse

    @GET("Playlists/{playlistId}/Items")
    suspend fun getPlaylistItems(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String,
        @Query("fields") fields: String = "PrimaryImageAspectRatio"
    ): ItemsResponse

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
