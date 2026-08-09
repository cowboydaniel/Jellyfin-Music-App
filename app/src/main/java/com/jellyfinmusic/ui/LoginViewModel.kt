package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repo: JellyfinRepository,
    settings: SettingsStore
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState(serverUrl = settings.current.jellyfinUrl))
    val state: StateFlow<LoginUiState> = _state

    fun onServerUrlChange(value: String) { _state.value = _state.value.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun onPasswordChange(value: String) { _state.value = _state.value.copy(password = value, error = null) }

    fun login(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.isLoading) return
        _state.value = s.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repo.login(s.serverUrl, s.username, s.password)
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                    onSuccess()
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = t.message ?: "Sign-in failed"
                    )
                }
        }
    }
}
