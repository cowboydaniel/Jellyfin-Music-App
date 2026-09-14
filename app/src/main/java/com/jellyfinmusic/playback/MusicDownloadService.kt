package com.jellyfinmusic.playback

import android.app.Notification
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.jellyfinmusic.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service that runs the download queue, so downloads keep going when
 * the app is backgrounded and survive it being closed.
 */
@UnstableApi
@AndroidEntryPoint
class MusicDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0
) {

    // Named to avoid clashing with the getDownloadManager() this class overrides.
    @Inject lateinit var injectedDownloadManager: DownloadManager

    private val notificationHelper: DownloadNotificationHelper by lazy {
        DownloadNotificationHelper(this, CHANNEL_ID)
    }

    override fun getDownloadManager(): DownloadManager = injectedDownloadManager

    override fun getScheduler(): Scheduler? = null

    /**
     * The progress notification media3 builds, plus a STOP action.
     *
     * Without it the only way to call off a large download is to find the
     * collection it came from, which is no use once several are queued -- so
     * the action cancels everything, running and queued alike.
     */
    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        val base = notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
        val stopIntent = android.app.PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, DownloadCommandReceiver::class.java)
                .setAction(DownloadCommandReceiver.ACTION_STOP_ALL),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
        // Recovered from the built notification rather than rebuilt from
        // scratch, so media3 keeps owning the progress text and bar.
        return NotificationCompat.Builder(this, base)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP ALL DOWNLOADS",
                stopIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2
        const val NOTIFICATION_IMPORTANCE = NotificationUtil.IMPORTANCE_LOW
    }
}
