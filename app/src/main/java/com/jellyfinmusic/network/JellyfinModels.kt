package com.jellyfinmusic.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthenticateRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String
)

@Serializable
data class AuthenticationResult(
    @SerialName("User") val user: JellyfinUser? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null
)

@Serializable
data class JellyfinUser(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null
)

@Serializable
data class ItemsResponse(
    @SerialName("Items") val items: List<BaseItem> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

@Serializable
data class BaseItem(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("AlbumId") val albumId: String? = null,
    @SerialName("ArtistItems") val artistItems: List<NameIdPair> = emptyList(),
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    @SerialName("UserData") val userData: UserItemData? = null,
    /**
     * The user a playlist belongs to. Jellyfin 10.9+ reports this; older
     * servers omit it, in which case ownership cannot be determined.
     */
    @SerialName("OwnerUserId") val ownerUserId: String? = null,
    /**
     * Identifies this row *within a playlist* rather than the underlying track,
     * and is the handle required to remove it. Only present when the item came
     * from a playlist query.
     */
    @SerialName("PlaylistItemId") val playlistItemId: String? = null
) {
    /** Duration in milliseconds; Jellyfin reports 100-nanosecond ticks. */
    val durationMs: Long get() = (runTimeTicks ?: 0L) / 10_000L

    val artistName: String?
        get() = albumArtist ?: artistItems.firstOrNull()?.name

    val isFavorite: Boolean get() = userData?.isFavorite == true
}

@Serializable
data class UserItemData(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlayCount") val playCount: Int = 0
)

@Serializable
data class CreatePlaylistRequest(
    @SerialName("Name") val name: String,
    @SerialName("Ids") val ids: List<String> = emptyList(),
    @SerialName("UserId") val userId: String,
    @SerialName("MediaType") val mediaType: String = "Audio"
)

@Serializable
data class CreatePlaylistResponse(
    @SerialName("Id") val id: String = ""
)

@Serializable
data class NameIdPair(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null
)

@Serializable
data class LyricsResponse(
    @SerialName("Lyrics") val lyrics: List<LyricLine> = emptyList()
)

@Serializable
data class LyricLine(
    @SerialName("Text") val text: String = "",
    /** Start offset in 100-nanosecond ticks; absent for unsynced lyrics. */
    @SerialName("Start") val start: Long? = null
) {
    val startMs: Long? get() = start?.let { it / 10_000L }
}
