package com.jellyfinmusic.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import com.jellyfinmusic.data.DownloadsController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the STOP button on the download notification.
 *
 * It calls the controller directly rather than sending the download service a
 * command, so stopping cannot end up queued behind the very downloads it is
 * meant to cancel.
 */
@UnstableApi
@AndroidEntryPoint
class DownloadCommandReceiver : BroadcastReceiver() {

    @Inject lateinit var downloads: DownloadsController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_ALL) downloads.stopAll()
    }

    companion object {
        const val ACTION_STOP_ALL = "com.jellyfinmusic.STOP_ALL_DOWNLOADS"
    }
}
