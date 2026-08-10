package com.jellyfinmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credentials and server configuration, persisted in EncryptedSharedPreferences.
 */
@Singleton
class SettingsStore @Inject constructor(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "jellyfin_music_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    val current: Settings get() = _state.value

    private fun read() = Settings(
        jellyfinUrl = prefs.getString(KEY_JELLYFIN_URL, "").orEmpty(),
        accessToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
        userId = prefs.getString(KEY_USER_ID, "").orEmpty(),
        username = prefs.getString(KEY_USERNAME, "").orEmpty(),
        lidarrUrl = prefs.getString(KEY_LIDARR_URL, "").orEmpty(),
        lidarrApiKey = prefs.getString(KEY_LIDARR_KEY, "").orEmpty(),
        lidarrRootFolder = prefs.getString(KEY_LIDARR_ROOT, "").orEmpty(),
        lidarrQualityProfileId = prefs.getInt(KEY_LIDARR_PROFILE, 1),
        lidarrMetadataProfileId = prefs.getInt(KEY_LIDARR_METADATA, 1),
        audioQuality = AudioQuality.fromName(prefs.getString(KEY_QUALITY, "").orEmpty()),
        normalizeVolume = prefs.getBoolean(KEY_NORMALIZE, true),
        playbackSpeed = prefs.getFloat(KEY_SPEED, 1.0f),
        downloadOverWifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
        smartDownloads = prefs.getBoolean(KEY_SMART_DOWNLOADS, false)
    )

    private fun publish() {
        _state.value = read()
    }

    fun saveSession(jellyfinUrl: String, token: String, userId: String, username: String) {
        prefs.edit()
            .putString(KEY_JELLYFIN_URL, jellyfinUrl.normalizeBaseUrl())
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
        publish()
    }

    fun saveJellyfinUrl(url: String) {
        prefs.edit().putString(KEY_JELLYFIN_URL, url.normalizeBaseUrl()).apply()
        publish()
    }

    fun saveLidarr(
        url: String,
        apiKey: String,
        rootFolder: String,
        qualityProfileId: Int,
        metadataProfileId: Int
    ) {
        prefs.edit()
            .putString(KEY_LIDARR_URL, url.normalizeBaseUrl())
            .putString(KEY_LIDARR_KEY, apiKey)
            .putString(KEY_LIDARR_ROOT, rootFolder)
            .putInt(KEY_LIDARR_PROFILE, qualityProfileId)
            .putInt(KEY_LIDARR_METADATA, metadataProfileId)
            .apply()
        publish()
    }

    /** Recent search terms, most recent first. */
    fun recentSearches(): List<String> =
        prefs.getString(KEY_RECENT_SEARCHES, "").orEmpty()
            .split("\n")
            .filter { it.isNotBlank() }

    fun addRecentSearch(term: String) {
        val trimmed = term.trim()
        if (trimmed.isBlank()) return
        // Re-searching an old term promotes it rather than duplicating it.
        val updated = (listOf(trimmed) + recentSearches().filterNot { it.equals(trimmed, true) })
            .take(MAX_RECENT_SEARCHES)
        prefs.edit().putString(KEY_RECENT_SEARCHES, updated.joinToString("\n")).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    fun setAudioQuality(quality: AudioQuality) {
        prefs.edit().putString(KEY_QUALITY, quality.name).apply()
        publish()
    }

    fun setNormalizeVolume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NORMALIZE, enabled).apply()
        publish()
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
        publish()
    }

    fun setDownloadOverWifiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, enabled).apply()
        publish()
    }

    fun setSmartDownloads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SMART_DOWNLOADS, enabled).apply()
        publish()
    }

    fun logout() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_RECENT_SEARCHES)
            .apply()
        publish()
    }

    private companion object {
        const val KEY_JELLYFIN_URL = "jellyfin_url"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USERNAME = "username"
        const val KEY_LIDARR_URL = "lidarr_url"
        const val KEY_LIDARR_KEY = "lidarr_api_key"
        const val KEY_LIDARR_ROOT = "lidarr_root_folder"
        const val KEY_LIDARR_PROFILE = "lidarr_quality_profile"
        const val KEY_LIDARR_METADATA = "lidarr_metadata_profile"
        const val KEY_RECENT_SEARCHES = "recent_searches"
        const val KEY_QUALITY = "audio_quality"
        const val KEY_NORMALIZE = "normalize_volume"
        const val KEY_SPEED = "playback_speed"
        const val KEY_WIFI_ONLY = "download_wifi_only"
        const val KEY_SMART_DOWNLOADS = "smart_downloads"
        const val MAX_RECENT_SEARCHES = 12
    }
}

/** Streaming tier. ORIGINAL asks the server for the untouched file. */
enum class AudioQuality(val label: String, val maxBitrate: Int?) {
    LOW("Low (128 kbps)", 128_000),
    NORMAL("Normal (192 kbps)", 192_000),
    HIGH("High (320 kbps)", 320_000),
    ORIGINAL("Original (no transcode)", null);

    companion object {
        fun fromName(name: String) = entries.firstOrNull { it.name == name } ?: HIGH
    }
}

data class Settings(
    val jellyfinUrl: String = "",
    val accessToken: String = "",
    val userId: String = "",
    val username: String = "",
    val lidarrUrl: String = "",
    val lidarrApiKey: String = "",
    val lidarrRootFolder: String = "",
    val lidarrQualityProfileId: Int = 1,
    val lidarrMetadataProfileId: Int = 1,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val normalizeVolume: Boolean = true,
    val playbackSpeed: Float = 1.0f,
    val downloadOverWifiOnly: Boolean = true,
    val smartDownloads: Boolean = false
) {
    val isLoggedIn: Boolean get() = jellyfinUrl.isNotBlank() && accessToken.isNotBlank() && userId.isNotBlank()
    val hasLidarr: Boolean get() = lidarrUrl.isNotBlank() && lidarrApiKey.isNotBlank()
}

/** Trims whitespace and guarantees a single trailing slash so Retrofit accepts it as a base URL. */
fun String.normalizeBaseUrl(): String {
    val trimmed = trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    return "$withScheme/"
}
