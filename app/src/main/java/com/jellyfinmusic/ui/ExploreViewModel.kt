package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.network.BaseItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val newReleases: List<BaseItem> = emptyList(),
    val genres: List<BaseItem> = emptyList(),
    val selectedGenre: String? = null,
    val genreAlbums: List<BaseItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repo: JellyfinRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state

    private var loaded = false

    fun loadOnce() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            runCatching {
                ExploreUiState(
                    newReleases = repo.latestAlbums(30),
                    genres = runCatching { repo.genres() }.getOrDefault(emptyList()),
                    isLoading = false
                )
            }
                .onSuccess { _state.value = it }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = it.message ?: "Could not reach the server"
                    )
                }
        }
    }

    /** Tapping a selected genre clears it, so the chip acts as a toggle. */
    fun selectGenre(genre: String) {
        if (_state.value.selectedGenre == genre) {
            _state.value = _state.value.copy(selectedGenre = null, genreAlbums = emptyList())
            return
        }
        _state.value = _state.value.copy(selectedGenre = genre, genreAlbums = emptyList())
        viewModelScope.launch {
            val albums = runCatching { repo.albumsByGenre(genre) }.getOrDefault(emptyList())
            if (_state.value.selectedGenre == genre) {
                _state.value = _state.value.copy(genreAlbums = albums)
            }
        }
    }

    fun imageUrl(item: BaseItem): String? = repo.artworkFor(item)
}
