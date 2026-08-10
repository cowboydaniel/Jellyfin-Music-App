package com.jellyfinmusic.data.imports

/**
 * Readers for the playlist formats people actually have to hand: M3U from a
 * local player, CSV from an export tool, or a list pasted out of anything.
 */
object PlaylistParsers {

    /**
     * M3U and M3U8.
     *
     * The useful metadata is on the #EXTINF line, conventionally
     * "#EXTINF:seconds,Artist - Title". Where that is missing, the file name is
     * the only clue left, so it is used as the title.
     */
    fun parseM3u(content: String): List<ImportTrack> {
        val tracks = mutableListOf<ImportTrack>()
        var pendingArtist = ""
        var pendingTitle = ""

        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val label = line.substringAfter(',', "").trim()
                    val (artist, title) = splitArtistTitle(label)
                    pendingArtist = artist
                    pendingTitle = title
                }

                // Other #directives (#EXTM3U, #PLAYLIST) carry nothing per-track.
                line.startsWith("#") -> Unit

                else -> {
                    val title = pendingTitle.ifBlank { fileNameOf(line) }
                    if (title.isNotBlank()) {
                        tracks += ImportTrack(
                            title = title,
                            artist = pendingArtist,
                            path = line
                        )
                    }
                    pendingArtist = ""
                    pendingTitle = ""
                }
            }
        }
        return tracks
    }

    /**
     * CSV as produced by the common export tools, whose column names differ but
     * always contain the words "track"/"name"/"title" and "artist". Falls back
     * to the first two columns when the header is unrecognised.
     */
    fun parseCsv(content: String): List<ImportTrack> {
        val rows = content.lineSequence()
            .map { splitCsvLine(it) }
            .filter { row -> row.any { it.isNotBlank() } }
            .toList()
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.lowercase().trim() }
        fun indexOf(vararg keys: String) =
            header.indexOfFirst { column -> keys.any { column.contains(it) } }

        val titleIndex = indexOf("track name", "title", "song", "name").takeIf { it >= 0 }
        val artistIndex = indexOf("artist").takeIf { it >= 0 }
        val albumIndex = indexOf("album").takeIf { it >= 0 }

        val hasHeader = titleIndex != null && artistIndex != null
        val body = if (hasHeader) rows.drop(1) else rows

        return body.mapNotNull { row ->
            val title = row.getOrNull(titleIndex ?: 0)?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            ImportTrack(
                title = title,
                artist = row.getOrNull(artistIndex ?: 1)?.trim().orEmpty(),
                album = row.getOrNull(albumIndex ?: -1)?.trim().orEmpty()
            )
        }
    }

    /** One track per line, in whatever "Artist - Title" order people paste. */
    fun parseText(content: String): List<ImportTrack> =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map {
                val (artist, title) = splitArtistTitle(it)
                ImportTrack(title = title, artist = artist)
            }
            .filter { it.title.isNotBlank() }
            .toList()

    /**
     * Splits "Artist - Title", the near-universal convention in exported lists
     * and video titles. Anything without a separator is treated as a title,
     * since a bare artist name is not something to search a track by.
     */
    fun splitArtistTitle(label: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", " — ", " | ")
        separators.forEach { separator ->
            val index = label.indexOf(separator)
            if (index > 0) {
                return label.take(index).trim() to label.substring(index + separator.length).trim()
            }
        }
        return "" to label.trim()
    }

    private fun fileNameOf(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
            .replace('_', ' ')
            .trim()

    /** Minimal CSV splitter that respects quoted fields containing commas. */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        fields += current.toString()
        return fields.map { it.trim().removeSurrounding("\"") }
    }
}
