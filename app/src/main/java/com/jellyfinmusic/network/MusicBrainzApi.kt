package com.jellyfinmusic.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * MusicBrainz recording search.
 *
 * Lidarr's own lookup only covers artists and albums, because an album is the
 * smallest thing it can monitor and grab. MusicBrainz indexes individual
 * recordings, so it is used to turn a song title into the release it appears
 * on — which is something Lidarr can then be asked for.
 */
interface MusicBrainzApi {

    @GET("ws/2/recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 25
    ): RecordingSearchResponse
}

@Serializable
data class RecordingSearchResponse(
    @SerialName("recordings") val recordings: List<MbRecording> = emptyList()
)

@Serializable
data class MbRecording(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    @SerialName("releases") val releases: List<MbRelease> = emptyList()
) {
    val artistName: String
        get() = artistCredit.joinToString("") { it.name + (it.joinPhrase ?: "") }
            .ifBlank { artistCredit.firstOrNull()?.artist?.name.orEmpty() }
}

@Serializable
data class MbArtistCredit(
    @SerialName("name") val name: String = "",
    @SerialName("joinphrase") val joinPhrase: String? = null,
    @SerialName("artist") val artist: MbArtist? = null
)

@Serializable
data class MbArtist(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = ""
)

@Serializable
data class MbRelease(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("release-group") val releaseGroup: MbReleaseGroup? = null
)

@Serializable
data class MbReleaseGroup(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String? = null,
    @SerialName("primary-type") val primaryType: String? = null
)
