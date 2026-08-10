package com.jellyfinmusic.playback

import android.app.Notification
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

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification = notificationHelper.buildProgressNotification(
        this,
        android.R.drawable.stat_sys_download,
        null,
        null,
        downloads,
        notMetRequirements
    )

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2
        const val NOTIFICATION_IMPORTANCE = NotificationUtil.IMPORTANCE_LOW
    }
}
