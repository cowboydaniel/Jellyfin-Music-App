package com.jellyfinmusic.data

import android.content.Context
import com.jellyfinmusic.network.LyricLine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores lyrics next to downloads.
 *
 * Lyrics are fetched from the server on demand, so a downloaded track would
 * otherwise have none once offline — which is exactly when it is playing from
 * disk. Caching them on download closes that gap.
 */
@Singleton
class LyricsCache @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun fileFor(itemId: String) = File(dir, "$itemId.json")

    fun read(itemId: String): List<LyricLine>? = runCatching {
        val file = fileFor(itemId)
        if (!file.exists()) return null
        json.decodeFromString(ListSerializer(LyricLine.serializer()), file.readText())
    }.getOrNull()

    fun write(itemId: String, lines: List<LyricLine>) {
        if (lines.isEmpty()) return
        runCatching {
            fileFor(itemId).writeText(
                json.encodeToString(ListSerializer(LyricLine.serializer()), lines)
            )
        }
    }

    fun remove(itemId: String) {
        runCatching { fileFor(itemId).delete() }
    }

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
