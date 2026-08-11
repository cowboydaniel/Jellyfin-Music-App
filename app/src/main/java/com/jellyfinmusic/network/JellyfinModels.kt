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
     * Integrated loudness in LUFS, reported by Jellyfin 10.9+. Used to even out
     * volume between tracks mastered at very different levels.
     */
    @SerialName("LUFS") val lufs: Double? = null,
    @SerialName("NormalizationGain") val normalizationGain: Double? = null,
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
    /** Thumbs rating: true up, false down, absent for no opinion. */
    @SerialName("Likes") val likes: Boolean? = null,
    @SerialName("Played") val played: Boolean = false,
    /** How far through the user is, 0–100; non-zero means partially played. */
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0L,
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

@Serializable
data class PlaybackReport(
    @SerialName("ItemId") val itemId: String,
    /** Jellyfin measures position in 100-nanosecond ticks. */
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
    @SerialName("EventName") val eventName: String? = null
)

@Serializable
data class DisplayPreferences(
    @SerialName("Id") val id: String = "",
    @SerialName("Client") val client: String = "",
    @SerialName("CustomPrefs") val customPrefs: Map<String, String> = emptyMap()
)

@Serializable
data class UpdatePlaylistRequest(
    @SerialName("Name") val name: String
)

@Serializable
data class JellyfinSession(
    @SerialName("Id") val id: String = "",
    @SerialName("DeviceName") val deviceName: String = "",
    @SerialName("Client") val client: String = "",
    @SerialName("UserName") val userName: String = "",
    @SerialName("DeviceId") val deviceId: String = "",
    @SerialName("SupportsRemoteControl") val supportsRemoteControl: Boolean = false,
    @SerialName("NowPlayingItem") val nowPlayingItem: BaseItem? = null
) {
    val label: String get() = deviceName.ifBlank { client }.ifBlank { "Unknown device" }
}
