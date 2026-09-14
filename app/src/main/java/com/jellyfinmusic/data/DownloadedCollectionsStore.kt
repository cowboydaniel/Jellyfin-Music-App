package com.jellyfinmusic.data

import android.content.Context
import com.jellyfinmusic.network.BaseItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A playlist or album that was downloaded as a unit.
 *
 * The track IDs are held in playlist order, which is the part that cannot be
 * recovered offline: the download index knows every downloaded track but has
 * no idea which collection any of them came from, or what order they went in.
 */
@Serializable
data class DownloadedCollection(
    val id: String,
    val name: String,
    /** Jellyfin's item type, so the UI can tell a playlist from an album. */
    val type: String,
    val artworkUrl: String?,
    val trackIds: List<String>
) {
    val isPlaylist: Boolean get() = type == "Playlist"
}

/**
 * Remembers which collections were downloaded, so they can be browsed and
 * played with no server.
 *
 * Downloading a playlist used to queue its tracks individually and discard
 * everything else, which left the user with a flat pile of songs offline and
 * no way to play the playlist itself.
 */
@Singleton
class DownloadedCollectionsStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("downloaded_collections", Context.MODE_PRIVATE)

    private val _collections = MutableStateFlow(read())
    val collections: StateFlow<List<DownloadedCollection>> = _collections.asStateFlow()

    fun put(collection: DownloadedCollection) {
        if (collection.trackIds.isEmpty()) return
        write(_collections.value.filterNot { it.id == collection.id } + collection)
    }

    fun remove(collectionId: String) {
        write(_collections.value.filterNot { it.id == collectionId })
    }

    fun byId(collectionId: String): DownloadedCollection? =
        _collections.value.firstOrNull { it.id == collectionId }

    /**
     * Drops tracks that are no longer downloaded, and forgets any collection
     * left with nothing, so removing downloads individually cannot leave a
     * playlist behind that plays silence.
     */
    fun prune(downloadedIds: Set<String>) {
        // Queuing a download is asynchronous, so an empty index more often
        // means "the service has not caught up" than "everything was deleted".
        // Removing the last download is handled by removeCollection instead.
        if (downloadedIds.isEmpty()) return
        val pruned = _collections.value
            .map { it.copy(trackIds = it.trackIds.filter { id -> id in downloadedIds }) }
            .filter { it.trackIds.isNotEmpty() }
        if (pruned != _collections.value) write(pruned)
    }

    fun clear() = write(emptyList())

    private fun write(value: List<DownloadedCollection>) {
        _collections.value = value.sortedBy { it.name.lowercase() }
        prefs.edit().putString(KEY, Json.encodeToString(SERIALIZER, value)).apply()
    }

    private fun read(): List<DownloadedCollection> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { Json.decodeFromString(SERIALIZER, raw) }
            .getOrDefault(emptyList())
            .sortedBy { it.name.lowercase() }
    }

    private companion object {
        const val KEY = "collections"
        val SERIALIZER = kotlinx.serialization.builtins.ListSerializer(
            DownloadedCollection.serializer()
        )
    }
}

/** Renders a stored collection as a library item, so existing UI can show it. */
fun DownloadedCollection.toBaseItem(): BaseItem = BaseItem(
    id = id,
    name = name,
    type = type,
    childCount = trackIds.size
)

/** Renders a downloaded track as a library item, for the offline track lists. */
fun SavedTrack.toBaseItem(): BaseItem = BaseItem(
    id = id,
    name = title,
    type = "Audio",
    albumArtist = artist,
    album = album
)
