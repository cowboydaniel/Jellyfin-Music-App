package com.jellyfinmusic.data

import com.jellyfinmusic.network.ApiProvider
import com.jellyfinmusic.network.AuthenticateRequest
import com.jellyfinmusic.network.BaseItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepository @Inject constructor(
    private val apis: ApiProvider,
    private val settings: SettingsStore
) {

    suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> =
        runCatching {
            val base = serverUrl.normalizeBaseUrl()
            require(base.isNotBlank()) { "Enter a server URL" }
            val api = apis.jellyfinForLogin(base)
            val result = api.authenticateByName(AuthenticateRequest(username, password))
            val token = result.accessToken
            val userId = result.user?.id
            require(!token.isNullOrBlank() && !userId.isNullOrBlank()) {
                "Server did not return an access token"
            }
            settings.saveSession(base, token, userId, result.user?.name ?: username)
        }

    suspend fun artists(): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicArtist",
        sortBy = "SortName"
    ).items

    suspend fun albumsOfArtist(artistId: String): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicAlbum",
        albumArtistIds = artistId,
        sortBy = "PremiereDate,SortName"
    ).items

    suspend fun tracksOfAlbum(albumId: String): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Audio",
        parentId = albumId,
        sortBy = "ParentIndexNumber,IndexNumber,SortName"
    ).items

    suspend fun playlists(): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Playlist",
        sortBy = "SortName"
    ).items

    suspend fun playlistTracks(playlistId: String): List<BaseItem> =
        apis.jellyfin().getPlaylistItems(playlistId, userId()).items

    /** Free-text search across artists and albums, used to match Lidarr results. */
    suspend fun search(term: String, types: String, limit: Int = 50): List<BaseItem> =
        apis.jellyfin().getItems(
            userId = userId(),
            includeItemTypes = types,
            searchTerm = term,
            limit = limit
        ).items

    fun streamUrl(itemId: String): String {
        val s = settings.current
        // Static=true asks the server for the original file rather than a transcode,
        // which ExoPlayer can handle directly for the common lossy/lossless formats.
        return "${s.jellyfinUrl}Audio/$itemId/universal" +
            "?UserId=${s.userId}" +
            "&DeviceId=jellyfin-music-android" +
            "&api_key=${s.accessToken}" +
            "&TranscodingContainer=ts" +
            "&TranscodingProtocol=hls" +
            "&AudioCodec=aac" +
            "&Container=opus,mp3,aac,m4a,flac,webma,webm,wav,ogg" +
            "&MaxStreamingBitrate=320000"
    }

    fun imageUrl(itemId: String?, tag: String? = null, maxSize: Int = 512): String? {
        if (itemId.isNullOrBlank()) return null
        val s = settings.current
        if (s.jellyfinUrl.isBlank()) return null
        val tagPart = if (tag.isNullOrBlank()) "" else "&tag=$tag"
        return "${s.jellyfinUrl}Items/$itemId/Images/Primary?fillHeight=$maxSize&fillWidth=$maxSize$tagPart"
    }

    /** Best available artwork for a track: its own image, else the album's. */
    fun artworkFor(item: BaseItem): String? {
        val ownTag = item.imageTags["Primary"]
        if (ownTag != null) return imageUrl(item.id, ownTag)
        if (item.albumId != null) return imageUrl(item.albumId, item.albumPrimaryImageTag)
        return null
    }

    private fun userId(): String = settings.current.userId.also {
        require(it.isNotBlank()) { "Not signed in" }
    }
}
