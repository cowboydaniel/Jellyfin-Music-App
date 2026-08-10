package com.jellyfinmusic.data

import com.jellyfinmusic.network.ApiProvider
import com.jellyfinmusic.network.AuthenticateRequest
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.network.CreatePlaylistRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinRepository @Inject constructor(
    private val apis: ApiProvider,
    private val settings: SettingsStore,
    private val lyricsCache: LyricsCache
) {

    suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> =
        runCatching {
            val base = serverUrl.normalizeBaseUrl()
            require(base.isNotBlank()) { "Enter a server URL" }
            val api = apis.jellyfinForLogin(base)
            val result = api.authenticateByName(AuthenticateRequest(username, password))
            val token = result.accessToken
            val userId = result.user?.id
            require(!token.isNullOrBlank() && !userId.isNullOrBlank()) {
                "Server did not return an access token"
            }
            settings.saveSession(base, token, userId, result.user?.name ?: username)
        }

    suspend fun artists(): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicArtist",
        sortBy = "SortName"
    ).items

    suspend fun albumsOfArtist(artistId: String): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicAlbum",
        albumArtistIds = artistId,
        sortBy = "PremiereDate,SortName"
    ).items

    suspend fun tracksOfAlbum(albumId: String): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Audio",
        parentId = albumId,
        sortBy = "ParentIndexNumber,IndexNumber,SortName"
    ).items.also { noteFavorites(it) }

    /**
     * Playlists belonging to the signed-in user.
     *
     * Jellyfin stores playlists in a library folder that every user can see, so
     * the server's own response can include other people's playlists. From
     * 10.9 each playlist carries an OwnerUserId, which is used here to keep the
     * list personal. Servers that omit the field cannot be filtered on — those
     * entries are left in rather than hidden, since dropping them would empty
     * the tab on older installs.
     */
    suspend fun playlists(): List<BaseItem> {
        val me = userId()
        return apis.jellyfin().getItems(
            userId = me,
            includeItemTypes = "Playlist",
            sortBy = "SortName"
        ).items.filter { it.ownerUserId == null || it.ownerUserId == me }
    }

    suspend fun playlistTracks(playlistId: String): List<BaseItem> =
        apis.jellyfin().getPlaylistItems(playlistId, userId()).items.also { noteFavorites(it) }

    suspend fun allAlbums(limit: Int? = null): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicAlbum",
        sortBy = "SortName",
        limit = limit
    ).items

    suspend fun allSongs(limit: Int = 500): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Audio",
        sortBy = "SortName",
        limit = limit
    ).items

    /** Newest additions to the library — the "New releases" shelf. */
    suspend fun latestAlbums(limit: Int = 20): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicAlbum",
        sortBy = "DateCreated",
        sortOrder = "Descending",
        limit = limit
    ).items

    /** Albums the user has actually played recently — the "Listen again" shelf. */
    suspend fun recentlyPlayedAlbums(limit: Int = 20): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicAlbum",
        sortBy = "DatePlayed",
        sortOrder = "Descending",
        filters = "IsPlayed",
        limit = limit
    ).items

    /** Most-played tracks, which seed the Quick picks grid. */
    suspend fun topSongs(limit: Int = 40): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Audio",
        sortBy = "PlayCount",
        sortOrder = "Descending",
        limit = limit
    ).items.also { noteFavorites(it) }

    /** Most recently played tracks — the source list for smart downloads. */
    suspend fun recentlyPlayedSongs(limit: Int = 50): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Audio",
        sortBy = "DatePlayed",
        sortOrder = "Descending",
        filters = "IsPlayed",
        limit = limit
    ).items.also { noteFavorites(it) }

    /** Random tracks, used to fill Quick picks on a library with no play history. */
    suspend fun randomSongs(limit: Int = 40): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Audio",
        sortBy = "Random",
        limit = limit
    ).items

    suspend fun topArtists(limit: Int = 20): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicArtist",
        sortBy = "SortName",
        limit = limit
    ).items

    suspend fun genres(): List<BaseItem> = apis.jellyfin().getMusicGenres(userId()).items

    suspend fun albumsByGenre(genre: String, limit: Int = 60): List<BaseItem> =
        apis.jellyfin().getItems(
            userId = userId(),
            includeItemTypes = "MusicAlbum",
            genres = genre,
            sortBy = "SortName",
            limit = limit
        ).items

    suspend fun itemById(id: String): BaseItem? = apis.jellyfin().getItems(
        userId = userId(),
        ids = id,
        recursive = false
    ).items.firstOrNull()

    /**
     * An artist's most-played tracks across every album, which is what an
     * artist page should lead with. Ordering only becomes meaningful once
     * playback reporting has built up some history; before then it falls back
     * to alphabetical, which the sort key already handles.
     */
    suspend fun topSongsOfArtist(artistId: String, limit: Int = 20): List<BaseItem> =
        apis.jellyfin().getItems(
            userId = userId(),
            includeItemTypes = "Audio",
            artistIds = artistId,
            sortBy = "PlayCount,SortName",
            sortOrder = "Descending",
            limit = limit
        ).items.also { noteFavorites(it) }

    /**
     * Lyrics for a track. The on-disk copy is preferred, so a downloaded track
     * keeps its lyrics with no connection; otherwise they are fetched and, if
     * the track is downloaded, kept.
     */
    suspend fun lyrics(itemId: String, cacheResult: Boolean = false): List<com.jellyfinmusic.network.LyricLine> {
        lyricsCache.read(itemId)?.let { return it }
        val fetched = runCatching { apis.jellyfin().getLyrics(itemId).lyrics }
            .getOrDefault(emptyList())
        if (cacheResult) lyricsCache.write(itemId, fetched)
        return fetched
    }

    /** Pulls lyrics down for a track that has just been downloaded. */
    suspend fun cacheLyricsFor(itemId: String) {
        if (lyricsCache.read(itemId) != null) return
        val fetched = runCatching { apis.jellyfin().getLyrics(itemId).lyrics }
            .getOrDefault(emptyList())
        lyricsCache.write(itemId, fetched)
    }

    fun dropCachedLyrics(itemId: String) = lyricsCache.remove(itemId)

    fun clearCachedLyrics() = lyricsCache.clear()

    /** Server-generated radio seeded from a track, album or artist. */
    suspend fun instantMix(itemId: String): List<BaseItem> =
        apis.jellyfin().getInstantMix(itemId, userId()).items

    // ---- Favourites -------------------------------------------------------

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Locally mirrored favourite state. Every list load folds its items in, and
     * toggles apply here first, so a tapped heart fills in immediately and stays
     * consistent across every screen showing that track.
     */
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    fun noteFavorites(items: List<BaseItem>) {
        if (items.isEmpty()) return
        val favorites = items.filter { it.isFavorite }.map { it.id }
        val known = items.map { it.id }.toSet()
        _favoriteIds.value = _favoriteIds.value
            // Items reported as not-favourite clear any stale local entry.
            .filterNot { it in known }
            .toSet() + favorites
    }

    fun isFavorite(itemId: String): Boolean = itemId in _favoriteIds.value

    /**
     * Drops everything cached about the signed-in user. Called on sign-out so a
     * second account never sees the first one's likes.
     */
    fun clearUserState() {
        _favoriteIds.value = emptySet()
    }

    /** Optimistic toggle; the local state is rolled back if the server refuses. */
    suspend fun toggleFavorite(itemId: String): Result<Boolean> {
        val wasFavorite = isFavorite(itemId)
        setLocalFavorite(itemId, !wasFavorite)
        return runCatching {
            if (wasFavorite) {
                apis.jellyfin().unmarkFavorite(userId(), itemId)
            } else {
                apis.jellyfin().markFavorite(userId(), itemId)
            }
            !wasFavorite
        }.onFailure { setLocalFavorite(itemId, wasFavorite) }
    }

    private fun setLocalFavorite(itemId: String, favorite: Boolean) {
        _favoriteIds.value = if (favorite) {
            _favoriteIds.value + itemId
        } else {
            _favoriteIds.value - itemId
        }
    }

    suspend fun favoriteSongs(): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "Audio",
        filters = "IsFavorite",
        sortBy = "SortName"
    ).items.also { noteFavorites(it) }

    suspend fun favoriteAlbums(limit: Int = 20): List<BaseItem> = apis.jellyfin().getItems(
        userId = userId(),
        includeItemTypes = "MusicAlbum",
        filters = "IsFavorite",
        sortBy = "SortName",
        limit = limit
    ).items

    // ---- Playlists --------------------------------------------------------

    /** Creates a playlist, optionally seeded with tracks, and returns its ID. */
    suspend fun createPlaylist(name: String, trackIds: List<String> = emptyList()): String =
        apis.jellyfin().createPlaylist(
            CreatePlaylistRequest(name = name, ids = trackIds, userId = userId())
        ).id

    suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        apis.jellyfin().addToPlaylist(playlistId, trackIds.joinToString(","), userId())
    }

    /** [entryIds] are PlaylistItemIds, which is what the remove endpoint expects. */
    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>) {
        if (entryIds.isEmpty()) return
        apis.jellyfin().removeFromPlaylist(playlistId, entryIds.joinToString(","))
    }

    suspend fun movePlaylistItem(playlistId: String, playlistItemId: String, newIndex: Int) {
        apis.jellyfin().movePlaylistItem(playlistId, playlistItemId, newIndex)
    }

    suspend fun deletePlaylist(playlistId: String) = apis.jellyfin().deleteItem(playlistId)

    /** Free-text search across artists and albums, used to match Lidarr results. */
    suspend fun search(term: String, types: String, limit: Int = 50): List<BaseItem> =
        apis.jellyfin().getItems(
            userId = userId(),
            includeItemTypes = types,
            searchTerm = term,
            limit = limit
        ).items

    /**
     * Stream URL for a track, shaped by the chosen quality tier.
     *
     * ORIGINAL uses the static endpoint, which serves the file untouched — the
     * only way to get real FLAC, and the only way gapless playback can work,
     * since a transcoded stream loses the encoder padding gapless relies on.
     */
    fun streamUrl(itemId: String): String {
        val s = settings.current
        val quality = s.audioQuality
        if (quality == AudioQuality.ORIGINAL) {
            return "${s.jellyfinUrl}Audio/$itemId/stream" +
                "?static=true" +
                "&api_key=${s.accessToken}" +
                "&deviceId=jellyfin-music-android"
        }
        return "${s.jellyfinUrl}Audio/$itemId/universal" +
            "?UserId=${s.userId}" +
            "&DeviceId=jellyfin-music-android" +
            "&api_key=${s.accessToken}" +
            "&TranscodingContainer=ts" +
            "&TranscodingProtocol=hls" +
            "&AudioCodec=aac" +
            "&Container=opus,mp3,aac,m4a,flac,webma,webm,wav,ogg" +
            "&MaxStreamingBitrate=${quality.maxBitrate}"
    }

    fun imageUrl(itemId: String?, tag: String? = null, maxSize: Int = 512): String? {
        if (itemId.isNullOrBlank()) return null
        val s = settings.current
        if (s.jellyfinUrl.isBlank()) return null
        val tagPart = if (tag.isNullOrBlank()) "" else "&tag=$tag"
        // maxHeight/maxWidth bound the image without changing its shape.
        // fillHeight/fillWidth would pad non-square art (video thumbnails, wide
        // covers) into a square with black bars baked in; cropping to a square
        // is the UI's job, via ContentScale.Crop.
        return "${s.jellyfinUrl}Items/$itemId/Images/Primary?maxHeight=$maxSize&maxWidth=$maxSize$tagPart"
    }

    /** Best available artwork for a track: its own image, else the album's. */
    fun artworkFor(item: BaseItem): String? {
        val ownTag = item.imageTags["Primary"]
        if (ownTag != null) return imageUrl(item.id, ownTag)
        if (item.albumId != null) return imageUrl(item.albumId, item.albumPrimaryImageTag)
        return null
    }

    private fun userId(): String = settings.current.userId.also {
        require(it.isNotBlank()) { "Not signed in" }
    }
}
