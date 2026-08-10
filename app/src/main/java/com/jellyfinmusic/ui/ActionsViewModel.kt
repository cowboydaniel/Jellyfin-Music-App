package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.BaseItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ActionsViewModel @Inject constructor(
    val actions: ActionsController,
    private val repo: JellyfinRepository
) : ViewModel() {

    val sheet = actions.sheet
    val playlists = actions.playlists
    val toast = actions.toast
    val favoriteIds = actions.favoriteIds

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)
}
