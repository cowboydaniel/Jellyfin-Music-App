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
    @SerialName("AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null
) {
    /** Duration in milliseconds; Jellyfin reports 100-nanosecond ticks. */
    val durationMs: Long get() = (runTimeTicks ?: 0L) / 10_000L

    val artistName: String?
        get() = albumArtist ?: artistItems.firstOrNull()?.name
}

@Serializable
data class NameIdPair(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null
)
