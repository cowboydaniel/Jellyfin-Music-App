package com.jellyfinmusic.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Items the user has said they are not interested in.
 *
 * Jellyfin records favourites and ratings but has no concept of "stop showing
 * me this", so this is kept locally and applied when building the Home shelves.
 */
@Singleton
class DismissedStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("dismissed_items", Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun dismiss(itemId: String) {
        val updated = _ids.value + itemId
        prefs.edit().putStringSet(KEY, updated).apply()
        _ids.value = updated
    }

    fun restore(itemId: String) {
        val updated = _ids.value - itemId
        prefs.edit().putStringSet(KEY, updated).apply()
        _ids.value = updated
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _ids.value = emptySet()
    }

    private companion object {
        const val KEY = "ids"
    }
}
