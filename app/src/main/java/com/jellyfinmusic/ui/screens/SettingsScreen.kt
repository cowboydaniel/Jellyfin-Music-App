package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.network.LidarrProfile
import com.jellyfinmusic.ui.SettingsViewModel
import com.jellyfinmusic.ui.theme.AppColors

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenDownloads: () -> Unit,
    onOpenRecap: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefsSnapshot by viewModel.playbackSettings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Jellyfin", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.jellyfinUrl,
            onValueChange = viewModel::onJellyfinUrlChange,
            label = { Text("Jellyfin server URL") },
            placeholder = { Text("http://192.168.1.10:8096") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (state.username.isNotBlank()) {
            Text(
                "Signed in as ${state.username}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        HorizontalDivider()

        Text("Lidarr", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.lidarrUrl,
            onValueChange = viewModel::onLidarrUrlChange,
            label = { Text("Lidarr server URL") },
            placeholder = { Text("http://192.168.1.10:8686") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.lidarrApiKey,
            onValueChange = viewModel::onLidarrApiKeyChange,
            label = { Text("Lidarr API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.rootFolder,
            onValueChange = viewModel::onRootFolderChange,
            label = { Text("Root folder path") },
            placeholder = { Text("/music") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Populated by "Test connection"; until then the saved IDs are used as-is.
        ProfilePicker(
            label = "Quality profile",
            profiles = state.qualityProfiles,
            selectedId = state.qualityProfileId,
            onSelected = viewModel::onQualityProfileChange
        )
        ProfilePicker(
            label = "Metadata profile",
            profiles = state.metadataProfiles,
            selectedId = state.metadataProfileId,
            onSelected = viewModel::onMetadataProfileChange
        )

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::save) { Text("Save") }
            OutlinedButton(onClick = viewModel::testLidarr, enabled = !state.isTesting) {
                if (state.isTesting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Test Lidarr connection")
                }
            }
        }

        HorizontalDivider()

        Text("Playlist import", style = MaterialTheme.typography.titleMedium)
        Text(
            "Optional. Spotify needs a free developer app — only the ID and secret, no " +
                "Spotify login. YouTube needs a Data API key. Both read public playlists only.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Secondary
        )
        var spotifyId by remember { mutableStateOf(prefsSnapshot.spotifyClientId) }
        var spotifySecret by remember { mutableStateOf(prefsSnapshot.spotifyClientSecret) }
        var youtubeKey by remember { mutableStateOf(prefsSnapshot.youtubeApiKey) }
        OutlinedTextField(
            value = spotifyId,
            onValueChange = { spotifyId = it },
            label = { Text("Spotify client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = spotifySecret,
            onValueChange = { spotifySecret = it },
            label = { Text("Spotify client secret") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = youtubeKey,
            onValueChange = { youtubeKey = it },
            label = { Text("YouTube API key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(
            onClick = { viewModel.saveImportCredentials(spotifyId, spotifySecret, youtubeKey) }
        ) { Text("Save import credentials") }

        HorizontalDivider()

        Text("Playback", style = MaterialTheme.typography.titleMedium)

        val prefs by viewModel.playbackSettings.collectAsStateWithLifecycle()

        Text(
            "Audio quality",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Secondary
        )
        com.jellyfinmusic.data.AudioQuality.entries.forEach { quality ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAudioQuality(quality) }
                    .padding(vertical = 10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = prefs.audioQuality == quality,
                    onClick = { viewModel.setAudioQuality(quality) }
                )
                Text(quality.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "Original streams the untouched file — the only setting that keeps " +
                "FLAC intact and allows gapless playback.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Secondary
        )

        ToggleRow(
            title = "Consistent volume",
            subtitle = "Level tracks against each other using the server's loudness data",
            checked = prefs.normalizeVolume,
            onChange = viewModel::setNormalizeVolume
        )

        Text(
            "Playback speed: ${"%.2f".format(prefs.playbackSpeed)}x",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Secondary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                OutlinedButton(onClick = { viewModel.setPlaybackSpeed(speed) }) {
                    Text("${speed}x")
                }
            }
        }

        HorizontalDivider()

        Text(
            "Your listening",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenRecap)
                .padding(vertical = 16.dp)
        )

        HorizontalDivider()

        Text(
            "Downloads and storage",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenDownloads)
                .padding(vertical = 16.dp)
        )

        HorizontalDivider()

        TextButton(onClick = {
            viewModel.logout()
            onLoggedOut()
        }) {
            Text("Sign out", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProfilePicker(
    label: String,
    profiles: List<LidarrProfile>,
    selectedId: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = profiles.firstOrNull { it.id == selectedId }?.name ?: "ID $selectedId"

    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { expanded = profiles.isNotEmpty() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedName)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        onSelected(profile.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
