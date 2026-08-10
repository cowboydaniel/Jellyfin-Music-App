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
        lidarrMetadataProfileId = prefs.getInt(KEY_LIDARR_METADATA, 1)
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
        const val MAX_RECENT_SEARCHES = 12
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
    val lidarrMetadataProfileId: Int = 1
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
