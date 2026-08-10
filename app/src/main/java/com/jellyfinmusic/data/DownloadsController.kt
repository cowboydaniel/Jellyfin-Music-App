package com.jellyfinmusic.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.MusicDownloadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What the UI needs to know about a track's offline state. */
enum class DownloadState { NONE, DOWNLOADING, DOWNLOADED, FAILED }

/**
 * Owns the offline download queue and exposes its state to the UI.
 *
 * Downloads are keyed by Jellyfin item ID, and the URL is rebuilt on demand,
 * so a download stays valid across sign-ins even though the stream URL carries
 * a token.
 */
@UnstableApi
@Singleton
class DownloadsController @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val cache: SimpleCache,
    private val repo: JellyfinRepository,
    private val settings: SettingsStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    /** Metadata for downloaded tracks, so the Downloads list works offline. */
    private val _downloadedTracks = MutableStateFlow<List<SavedTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<SavedTrack>> = _downloadedTracks.asStateFlow()

    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            // Lyrics are fetched live, so they are pulled down alongside the
            // audio the moment a download completes.
            if (download.state == Download.STATE_COMPLETED) {
                scope.launch { runCatching { repo.cacheLyricsFor(download.request.id) } }
            }
            refresh()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            repo.dropCachedLyrics(download.request.id)
            refresh()
        }
    }

    init {
        downloadManager.addListener(listener)
        applyNetworkRequirement()
        refresh()
    }

    /**
     * Holds downloads until unmetered network when the user asks for it, rather
     * than silently spending mobile data on a whole album.
     */
    fun applyNetworkRequirement() {
        val requirements = if (settings.current.downloadOverWifiOnly) {
            androidx.media3.exoplayer.scheduler.Requirements(
                androidx.media3.exoplayer.scheduler.Requirements.NETWORK_UNMETERED
            )
        } else {
            androidx.media3.exoplayer.scheduler.Requirements(
                androidx.media3.exoplayer.scheduler.Requirements.NETWORK
            )
        }
        runCatching { downloadManager.requirements = requirements }
    }

    /**
     * Keeps the most recently played tracks downloaded and drops the rest.
     *
     * Only tracks it added are removed again, so an explicit download is never
     * deleted by the automatic pass.
     */
    fun syncSmartDownloads(recentIds: List<BaseItem>) {
        if (!settings.current.smartDownloads) return
        scope.launch {
            val keep = recentIds.take(SMART_DOWNLOAD_LIMIT)
            val keepIds = keep.map { it.id }.toSet()
            keep.filter { stateOf(it.id) == DownloadState.NONE }.forEach { download(it, smart = true) }
            smartDownloadIds
                .filterNot { it in keepIds }
                .forEach {
                    remove(it)
                    smartDownloadIds.remove(it)
                }
        }
    }

    /** IDs this controller downloaded automatically, so they can be reclaimed. */
    private val smartDownloadIds = mutableSetOf<String>()

    fun stateOf(itemId: String): DownloadState = _states.value[itemId] ?: DownloadState.NONE

    /**
     * Queues a track. The display metadata rides along in the request's data
     * field so the Downloads list can be rendered without the server.
     */
    fun download(item: BaseItem, smart: Boolean = false) {
        if (smart) smartDownloadIds.add(item.id)
        val request = DownloadRequest.Builder(item.id, android.net.Uri.parse(repo.streamUrl(item.id)))
            .setData(
                SavedTrack(
                    id = item.id,
                    title = item.name.orEmpty(),
                    artist = item.artistName.orEmpty(),
                    album = item.album.orEmpty(),
                    artworkUrl = repo.artworkFor(item)
                ).encode()
            )
            .build()
        DownloadService.sendAddDownload(
            context,
            MusicDownloadService::class.java,
            request,
            /* foreground = */ false
        )
    }

    fun downloadAll(items: List<BaseItem>) = items.forEach(::download)

    fun remove(itemId: String) {
        DownloadService.sendRemoveDownload(
            context,
            MusicDownloadService::class.java,
            itemId,
            /* foreground = */ false
        )
    }

    fun removeAll() {
        DownloadService.sendRemoveAllDownloads(
            context,
            MusicDownloadService::class.java,
            /* foreground = */ false
        )
    }

    /** Bytes currently held on disk by downloaded audio. */
    fun cacheSizeBytes(): Long = runCatching { cache.cacheSpace }.getOrDefault(0L)

    fun refresh() {
        scope.launch {
            val (states, tracks) = withContext(Dispatchers.IO) {
                val stateMap = mutableMapOf<String, DownloadState>()
                val trackList = mutableListOf<SavedTrack>()
                runCatching {
                    downloadManager.downloadIndex.getDownloads().use { cursor ->
                        while (cursor.moveToNext()) {
                            val download = cursor.download
                            val id = download.request.id
                            stateMap[id] = when (download.state) {
                                Download.STATE_COMPLETED -> DownloadState.DOWNLOADED
                                Download.STATE_FAILED -> DownloadState.FAILED
                                Download.STATE_REMOVING -> DownloadState.NONE
                                else -> DownloadState.DOWNLOADING
                            }
                            if (download.state == Download.STATE_COMPLETED) {
                                SavedTrack.decode(download.request.data)?.let(trackList::add)
                            }
                        }
                    }
                }
                stateMap to trackList.sortedBy { it.title.lowercase() }
            }
            _states.value = states
            _downloadedTracks.value = tracks
        }
    }

    /** Rebuilds a playable track from saved metadata, for offline playback. */
    fun toPlayable(track: SavedTrack) = com.jellyfinmusic.playback.PlayableTrack(
        id = track.id,
        title = track.title,
        artist = track.artist,
        album = track.album,
        streamUrl = repo.streamUrl(track.id),
        artworkUrl = track.artworkUrl
    )

    private companion object {
        const val SMART_DOWNLOAD_LIMIT = 50
    }

    fun clearForSignOut() {
        removeAll()
        repo.clearCachedLyrics()
        _states.value = emptyMap()
        _downloadedTracks.value = emptyList()
    }
}

private fun SavedTrack.encode(): ByteArray =
    kotlinx.serialization.json.Json.encodeToString(SavedTrack.serializer(), this).toByteArray()

private fun SavedTrack.Companion.decode(bytes: ByteArray): SavedTrack? = runCatching {
    kotlinx.serialization.json.Json.decodeFromString(SavedTrack.serializer(), String(bytes))
}.getOrNull()
