package com.jellyfinmusic.network

import com.jellyfinmusic.data.SettingsStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds Retrofit clients lazily, because server URLs and credentials are only
 * known at runtime and can change from the settings screen. Clients are cached
 * against the configuration they were built from and rebuilt when it changes.
 */
@Singleton
class ApiProvider @Inject constructor(
    private val settings: SettingsStore
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        coerceInputValues = true
    }
    private val converter = json.asConverterFactory("application/json".toMediaType())

    private var jellyfinCacheKey: String? = null
    private var jellyfinApi: JellyfinApi? = null

    private var lidarrCacheKey: String? = null
    private var lidarrApi: LidarrApi? = null

    /** The Jellyfin device identity sent on every request; also shown in the server's device list. */
    private val deviceId: String = "jellyfin-music-android"

    @Synchronized
    fun jellyfin(): JellyfinApi {
        val s = settings.current
        require(s.jellyfinUrl.isNotBlank()) { "Jellyfin server URL is not configured" }
        val key = s.jellyfinUrl + "|" + s.accessToken
        jellyfinApi?.let { if (key == jellyfinCacheKey) return it }

        val client = baseClient()
            .addInterceptor { chain ->
                val token = settings.current.accessToken
                val auth = buildString {
                    append("MediaBrowser Client=\"Jellyfin Music\"")
                    append(", Device=\"Android\"")
                    append(", DeviceId=\"$deviceId\"")
                    append(", Version=\"1.0\"")
                    if (token.isNotBlank()) append(", Token=\"$token\"")
                }
                val request = chain.request().newBuilder()
                    .header("Authorization", auth)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        val api = Retrofit.Builder()
            .baseUrl(s.jellyfinUrl)
            .client(client)
            .addConverterFactory(converter)
            .build()
            .create(JellyfinApi::class.java)

        jellyfinCacheKey = key
        jellyfinApi = api
        return api
    }

    @Synchronized
    fun lidarr(): LidarrApi {
        val s = settings.current
        require(s.hasLidarr) { "Lidarr server URL and API key are not configured" }
        val key = s.lidarrUrl + "|" + s.lidarrApiKey
        lidarrApi?.let { if (key == lidarrCacheKey) return it }

        val client = baseClient()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("X-Api-Key", settings.current.lidarrApiKey)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()

        val api = Retrofit.Builder()
            .baseUrl(s.lidarrUrl)
            .client(client)
            .addConverterFactory(converter)
            .build()
            .create(LidarrApi::class.java)

        lidarrCacheKey = key
        lidarrApi = api
        return api
    }

    private var musicBrainzApi: MusicBrainzApi? = null
    private var spotifyAuthApi: SpotifyAuthApi? = null
    private var spotifyApi: SpotifyApi? = null
    private var youTubeApi: YouTubeApi? = null

    @Synchronized
    fun spotifyAuth(): SpotifyAuthApi = spotifyAuthApi ?: Retrofit.Builder()
        .baseUrl("https://accounts.spotify.com/")
        .client(baseClient().build())
        .addConverterFactory(converter)
        .build()
        .create(SpotifyAuthApi::class.java)
        .also { spotifyAuthApi = it }

    @Synchronized
    fun spotify(): SpotifyApi = spotifyApi ?: Retrofit.Builder()
        .baseUrl("https://api.spotify.com/")
        .client(baseClient().build())
        .addConverterFactory(converter)
        .build()
        .create(SpotifyApi::class.java)
        .also { spotifyApi = it }

    @Synchronized
    fun youTube(): YouTubeApi = youTubeApi ?: Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(baseClient().build())
        .addConverterFactory(converter)
        .build()
        .create(YouTubeApi::class.java)
        .also { youTubeApi = it }

    /**
     * MusicBrainz is a fixed public endpoint, so this client never needs
     * rebuilding. Their terms require an application-identifying User-Agent.
     */
    @Synchronized
    fun musicBrainz(): MusicBrainzApi {
        musicBrainzApi?.let { return it }
        val client = baseClient()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header(
                            "User-Agent",
                            "JellyfinMusic/1.0 (https://github.com/cowboydaniel/Jellyfin-Music-App)"
                        )
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://musicbrainz.org/")
            .client(client)
            .addConverterFactory(converter)
            .build()
            .create(MusicBrainzApi::class.java)
            .also { musicBrainzApi = it }
    }

    /**
     * Authenticating happens before a token exists, and possibly against a URL
     * that has not been saved yet, so it needs a one-off client.
     */
    fun jellyfinForLogin(baseUrl: String): JellyfinApi {
        val client = baseClient()
            .addInterceptor { chain ->
                val auth = "MediaBrowser Client=\"Jellyfin Music\", Device=\"Android\", " +
                    "DeviceId=\"$deviceId\", Version=\"1.0\""
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", auth)
                        .header("Accept", "application/json")
                        .build()
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(converter)
            .build()
            .create(JellyfinApi::class.java)
    }

    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
}
