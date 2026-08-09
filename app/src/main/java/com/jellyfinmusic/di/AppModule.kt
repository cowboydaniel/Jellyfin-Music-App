package com.jellyfinmusic.di

import android.content.Context
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.playback.PlayerConnection
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    fun providePlayerConnection(@ApplicationContext context: Context): PlayerConnection =
        PlayerConnection(context)
}
