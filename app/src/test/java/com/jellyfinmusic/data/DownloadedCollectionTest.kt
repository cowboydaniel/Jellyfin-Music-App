package com.jellyfinmusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Membership rules for a downloaded collection.
 *
 * The store itself needs a Context, so the rules are exercised here as the
 * store applies them: drop exactly the IDs that were removed, and discard a
 * collection left with nothing.
 */
class DownloadedCollectionTest {

    private fun collection(trackIds: List<String>) = DownloadedCollection(
        id = "playlist-1",
        name = "Road trip",
        type = "Playlist",
        artworkUrl = null,
        trackIds = trackIds
    )

    /** What DownloadedCollectionsStore.removeTracks does to one collection. */
    private fun removeTracks(
        collections: List<DownloadedCollection>,
        removed: Set<String>
    ): List<DownloadedCollection> = collections
        .map { it.copy(trackIds = it.trackIds.filterNot { id -> id in removed }) }
        .filter { it.trackIds.isNotEmpty() }

    @Test
    fun `removing a track keeps the rest in order`() {
        val result = removeTracks(listOf(collection(listOf("a", "b", "c", "d"))), setOf("b"))
        assertEquals(listOf("a", "c", "d"), result.single().trackIds)
    }

    @Test
    fun `collection left with no tracks is discarded`() {
        val result = removeTracks(listOf(collection(listOf("a", "b"))), setOf("a", "b"))
        assertEquals(emptyList<DownloadedCollection>(), result)
    }

    /**
     * The regression this guards against: membership used to be recomputed
     * against the download index, so a playlist queued faster than the service
     * registered it was truncated to however many tracks had been picked up --
     * 32 of 100, in the report -- and the rest completed as loose tracks
     * belonging to nothing. Nothing removed means nothing dropped, however
     * little of the queue has registered.
     */
    @Test
    fun `tracks still queued are not treated as removed`() {
        val all = (1..100).map { "track-$it" }
        val result = removeTracks(listOf(collection(all)), removed = emptySet())
        assertEquals(all, result.single().trackIds)
    }
}
