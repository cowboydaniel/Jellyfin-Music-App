package com.jellyfinmusic.ui

import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.playback.PlayableTrack

/** Turns a Jellyfin Audio item into something the player can queue. */
fun BaseItem.toPlayable(repo: JellyfinRepository) = PlayableTrack(
    id = id,
    title = name.orEmpty(),
    artist = artistName.orEmpty(),
    album = album.orEmpty(),
    streamUrl = repo.streamUrl(id),
    artworkUrl = repo.artworkFor(this)
)

fun List<BaseItem>.toPlayable(repo: JellyfinRepository) = map { it.toPlayable(repo) }
