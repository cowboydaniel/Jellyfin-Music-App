package com.jellyfinmusic.data

import com.jellyfinmusic.network.ApiProvider
import com.jellyfinmusic.network.PlaybackReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the server what is being played.
 *
 * Jellyfin only counts a play and stamps a last-played date if the client
 * reports it, so without this every shelf ranked on listening history — Quick
 * picks, Listen again — stays empty however much is listened to. It also puts
 * the app in the server's active-sessions list.
 *
 * Failures are swallowed: reporting is bookkeeping, and a flaky network should
 * never interrupt playback.
 */
@Singleton
class PlaybackReporter @Inject constructor(
    private val apis: ApiProvider,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The track currently reported as playing, so it can be closed out properly. */
    private var openItemId: String? = null

    fun onStart(itemId: String, positionMs: Long) {
        if (itemId == openItemId) return
        // A new track implies the previous one finished.
        openItemId?.let { previous -> report(previous) { api, body -> api.reportPlaybackStopped(body) } }
        openItemId = itemId
        report(itemId, positionMs) { api, body -> api.reportPlaybackStart(body) }
    }

    fun onProgress(itemId: String, positionMs: Long, isPaused: Boolean) {
        if (openItemId != itemId) {
            onStart(itemId, positionMs)
            return
        }
        report(itemId, positionMs, isPaused, event = if (isPaused) "Pause" else "TimeUpdate") { api, body ->
            api.reportPlaybackProgress(body)
        }
    }

    fun onStop(itemId: String?, positionMs: Long) {
        val id = itemId ?: openItemId ?: return
        openItemId = null
        report(id, positionMs) { api, body -> api.reportPlaybackStopped(body) }
    }

    private fun report(
        itemId: String,
        positionMs: Long = 0L,
        isPaused: Boolean = false,
        event: String? = null,
        call: suspend (com.jellyfinmusic.network.JellyfinApi, PlaybackReport) -> Unit
    ) {
        if (!settings.current.isLoggedIn) return
        scope.launch {
            runCatching {
                call(
                    apis.jellyfin(),
                    PlaybackReport(
                        itemId = itemId,
                        positionTicks = positionMs.coerceAtLeast(0L) * 10_000L,
                        isPaused = isPaused,
                        eventName = event
                    )
                )
            }
        }
    }
}
