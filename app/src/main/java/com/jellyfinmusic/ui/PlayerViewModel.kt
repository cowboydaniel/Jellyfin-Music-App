package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: PlayerConnection
) : ViewModel() {
    val state = player.state
}
