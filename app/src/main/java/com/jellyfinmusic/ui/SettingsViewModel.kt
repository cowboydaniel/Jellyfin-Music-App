package com.jellyfinmusic.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellyfinmusic.data.ActionsController
import com.jellyfinmusic.data.JellyfinRepository
import com.jellyfinmusic.data.LidarrRepository
import com.jellyfinmusic.data.PlaybackStateStore
import com.jellyfinmusic.data.SettingsStore
import com.jellyfinmusic.network.LidarrProfile
import com.jellyfinmusic.network.LidarrRootFolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val jellyfinUrl: String = "",
    val username: String = "",
    val lidarrUrl: String = "",
    val lidarrApiKey: String = "",
    val rootFolder: String = "",
    val qualityProfileId: Int = 1,
    val metadataProfileId: Int = 1,
    val rootFolders: List<LidarrRootFolder> = emptyList(),
    val qualityProfiles: List<LidarrProfile> = emptyList(),
    val metadataProfiles: List<LidarrProfile> = emptyList(),
    val isTesting: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val lidarr: LidarrRepository,
    private val jellyfin: JellyfinRepository,
    private val actions: ActionsController,
    private val playbackState: PlaybackStateStore,
    private val downloads: com.jellyfinmusic.data.DownloadsController,
    private val player: com.jellyfinmusic.playback.PlayerConnection
) : ViewModel() {

    val downloadedTracks = downloads.downloadedTracks
    val playbackSettings = settings.state

    fun setAudioQuality(quality: com.jellyfinmusic.data.AudioQuality) =
        settings.setAudioQuality(quality)

    fun setNormalizeVolume(enabled: Boolean) = settings.setNormalizeVolume(enabled)

    fun setPlaybackSpeed(speed: Float) {
        settings.setPlaybackSpeed(speed)
        player.setPlaybackSpeed(speed)
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        settings.setDownloadOverWifiOnly(enabled)
        downloads.applyNetworkRequirement()
    }

    fun setSmartDownloads(enabled: Boolean) = settings.setSmartDownloads(enabled)

    fun saveImportCredentials(spotifyId: String, spotifySecret: String, youtubeKey: String) {
        settings.saveImportCredentials(spotifyId, spotifySecret, youtubeKey)
        _state.value = _state.value.copy(message = "Import credentials saved")
    }

    fun refreshDownloads() = downloads.refresh()

    fun downloadCacheBytes(): Long = downloads.cacheSizeBytes()

    fun clearDownloads() {
        downloads.removeAll()
        downloads.refresh()
    }

    private val _state = MutableStateFlow(
        settings.current.let {
            SettingsUiState(
                jellyfinUrl = it.jellyfinUrl,
                username = it.username,
                lidarrUrl = it.lidarrUrl,
                lidarrApiKey = it.lidarrApiKey,
                rootFolder = it.lidarrRootFolder,
                qualityProfileId = it.lidarrQualityProfileId,
                metadataProfileId = it.lidarrMetadataProfileId
            )
        }
    )
    val state: StateFlow<SettingsUiState> = _state

    fun onJellyfinUrlChange(v: String) { _state.value = _state.value.copy(jellyfinUrl = v) }
    fun onLidarrUrlChange(v: String) { _state.value = _state.value.copy(lidarrUrl = v) }
    fun onLidarrApiKeyChange(v: String) { _state.value = _state.value.copy(lidarrApiKey = v) }
    fun onRootFolderChange(v: String) { _state.value = _state.value.copy(rootFolder = v) }
    fun onQualityProfileChange(v: Int) { _state.value = _state.value.copy(qualityProfileId = v) }
    fun onMetadataProfileChange(v: Int) { _state.value = _state.value.copy(metadataProfileId = v) }
    fun dismissMessage() { _state.value = _state.value.copy(message = null, error = null) }

    fun save() {
        val s = _state.value
        settings.saveJellyfinUrl(s.jellyfinUrl)
        settings.saveLidarr(
            url = s.lidarrUrl,
            apiKey = s.lidarrApiKey,
            rootFolder = s.rootFolder,
            qualityProfileId = s.qualityProfileId,
            metadataProfileId = s.metadataProfileId
        )
        _state.value = _state.value.copy(message = "Settings saved")
    }

    /**
     * Verifies the Lidarr credentials and pulls the lists the request flow needs
     * (root folder, quality and metadata profiles) so they can be picked rather
     * than typed.
     */
    fun testLidarr() {
        save()
        _state.value = _state.value.copy(isTesting = true, message = null, error = null)
        viewModelScope.launch {
            runCatching {
                Triple(lidarr.rootFolders(), lidarr.qualityProfiles(), lidarr.metadataProfiles())
            }
                .onSuccess { (roots, quality, metadata) ->
                    _state.value = _state.value.copy(
                        isTesting = false,
                        rootFolders = roots,
                        qualityProfiles = quality,
                        metadataProfiles = metadata,
                        rootFolder = _state.value.rootFolder.ifBlank { roots.firstOrNull()?.path.orEmpty() },
                        qualityProfileId = quality.firstOrNull { it.id == _state.value.qualityProfileId }?.id
                            ?: quality.firstOrNull()?.id ?: 1,
                        metadataProfileId = metadata.firstOrNull { it.id == _state.value.metadataProfileId }?.id
                            ?: metadata.firstOrNull()?.id ?: 1,
                        message = "Connected to Lidarr — ${roots.size} root folder(s) found"
                    )
                    save()
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        isTesting = false,
                        error = t.message ?: "Could not reach Lidarr"
                    )
                }
        }
    }

    fun logout() {
        // Clear per-user caches before the session goes, so signing in as
        // someone else starts from a clean slate.
        jellyfin.clearUserState()
        actions.clearUserState()
        playbackState.clear()
        settings.logout()
    }
}
