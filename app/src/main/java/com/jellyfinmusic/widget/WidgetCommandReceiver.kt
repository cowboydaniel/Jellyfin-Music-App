package com.jellyfinmusic.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jellyfinmusic.playback.PlayerConnection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Turns widget button presses into player commands.
 *
 * The widget cannot hold a MediaController, so it broadcasts an intent and this
 * receiver — which lives in the app's process — forwards it to the player.
 */
@AndroidEntryPoint
class WidgetCommandReceiver : BroadcastReceiver() {

    @Inject lateinit var player: PlayerConnection

    override fun onReceive(context: Context, intent: Intent) {
        // The controller may not be connected if the app has not been opened
        // since boot, so connecting first is not optional here.
        player.connect()
        when (intent.action) {
            NowPlayingWidget.ACTION_PLAY_PAUSE -> player.playPause()
            NowPlayingWidget.ACTION_NEXT -> player.next()
            NowPlayingWidget.ACTION_PREVIOUS -> player.previous()
        }
    }
}
