package com.jellyfinmusic.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * Offline download plumbing.
 *
 * The same cache backs both downloading and playback: once a track has been
 * downloaded, [CacheDataSource] serves it from disk, so playing offline needs
 * no separate code path or "is this downloaded" check at play time.
 */
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context) =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider
    ): SimpleCache = SimpleCache(
        File(context.filesDir, "downloads"),
        // Downloads are explicit user choices, so nothing is evicted behind
        // their back; removal happens only when they delete a download.
        NoOpCacheEvictor(),
        databaseProvider
    )

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: StandaloneDatabaseProvider,
        cache: SimpleCache
    ): DownloadManager = DownloadManager(
        context,
        databaseProvider,
        cache,
        DefaultHttpDataSource.Factory(),
        Executors.newFixedThreadPool(2)
    ).apply {
        maxParallelDownloads = 2
    }

    /**
     * Reads through the download cache and falls back to the network, so a
     * downloaded track plays from disk and everything else streams.
     */
    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(cache: SimpleCache): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
