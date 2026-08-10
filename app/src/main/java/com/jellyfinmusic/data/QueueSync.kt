package com.jellyfinmusic.data

import com.jellyfinmusic.network.ApiProvider
import com.jellyfinmusic.network.DisplayPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The queue as another device last left it.
 *
 * Only IDs and a position are stored — enough to rebuild the queue, small
 * enough to live in a preferences value, and free of the access token that a
 * stored stream URL would carry.
 */
@Serializable
data class RemoteQueue(
    val trackIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val updatedAt: Long = 0L,
    val deviceName: String = ""
)

/**
 * Syncs the playing queue between devices through Jellyfin.
 *
 * Jellyfin has no queue API, but it does have per-user key/value storage in
 * DisplayPreferences, which is enough to hand a queue from phone to desktop.
 * Whichever side wrote most recently wins; there is no merge, because two
 * devices playing different things have no sensible middle.
 */
@Singleton
class QueueSync @Inject constructor(
    private val apis: ApiProvider,
    private val settings: SettingsStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun push(trackIds: List<String>, currentIndex: Int, positionMs: Long) {
        if (trackIds.isEmpty()) return
        val s = settings.current
        if (!s.isLoggedIn) return
        val payload = RemoteQueue(
            // Capped so the payload stays comfortably inside a preferences value.
            trackIds = trackIds.take(MAX_TRACKS),
            currentIndex = currentIndex.coerceIn(0, trackIds.lastIndex),
            positionMs = positionMs,
            updatedAt = System.currentTimeMillis(),
            deviceName = android.os.Build.MODEL.orEmpty()
        )
        runCatching {
            apis.jellyfin().updateDisplayPreferences(
                id = PREF_ID,
                userId = s.userId,
                client = CLIENT,
                body = DisplayPreferences(
                    id = PREF_ID,
                    client = CLIENT,
                    customPrefs = mapOf(
                        KEY_QUEUE to json.encodeToString(RemoteQueue.serializer(), payload)
                    )
                )
            )
        }
    }

    suspend fun pull(): RemoteQueue? {
        val s = settings.current
        if (!s.isLoggedIn) return null
        return runCatching {
            val prefs = apis.jellyfin().getDisplayPreferences(PREF_ID, s.userId, CLIENT)
            prefs.customPrefs[KEY_QUEUE]?.let {
                json.decodeFromString(RemoteQueue.serializer(), it)
            }
        }.getOrNull()?.takeIf { it.trackIds.isNotEmpty() }
    }

    private companion object {
        const val PREF_ID = "jellyfin-music-queue"
        const val CLIENT = "jellyfin-music-android"
        const val KEY_QUEUE = "queue"
        const val MAX_TRACKS = 200
    }
}
