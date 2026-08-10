package com.jellyfinmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The queue as it stood when the app was last closed.
 *
 * Only stable identity and display metadata are kept. Stream URLs carry the
 * access token and would go stale on the next sign-in, so they are rebuilt from
 * the item ID at restore time.
 */
@Serializable
data class SavedQueue(
    val tracks: List<SavedTrack> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
    /** When this device last wrote the queue, for comparison with the server copy. */
    val savedAt: Long = 0L
)

@Serializable
data class SavedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String? = null
) {
    companion object
}

/**
 * Persists the playing queue so a relaunch resumes where the user left off.
 *
 * Media3 keeps its state only for the life of the process, so closing the app
 * would otherwise lose the queue entirely.
 */
@Singleton
class PlaybackStateStore @Inject constructor(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "jellyfin_music_playback_state",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(queue: SavedQueue) {
        // An empty queue means nothing is loaded; clear rather than store it,
        // so a restart does not resurrect a stale mini player.
        if (queue.tracks.isEmpty()) {
            clear()
            return
        }
        runCatching {
            json.encodeToString(SavedQueue.serializer(), queue.copy(savedAt = System.currentTimeMillis()))
        }
            .onSuccess { prefs.edit().putString(KEY_QUEUE, it).apply() }
    }

    fun load(): SavedQueue? {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return null
        return runCatching { json.decodeFromString(SavedQueue.serializer(), raw) }
            .getOrNull()
            ?.takeIf { it.tracks.isNotEmpty() }
    }

    fun clear() {
        prefs.edit().remove(KEY_QUEUE).apply()
    }

    private companion object {
        const val KEY_QUEUE = "saved_queue"
    }
}
