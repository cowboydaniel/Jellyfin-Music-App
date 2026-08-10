package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.ui.SettingsViewModel
import com.jellyfinmusic.ui.theme.AppColors

/**
 * Storage view for offline downloads: how much space they take, what is held,
 * and a way to clear them.
 */
@Composable
fun DownloadsSettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val tracks by viewModel.downloadedTracks.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var usedBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(tracks) {
        viewModel.refreshDownloads()
        usedBytes = viewModel.downloadCacheBytes()
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = AppColors.Surface,
            title = { Text("Clear downloads?") },
            text = { Text("All ${tracks.size} downloaded tracks will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearDownloads()
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            )
    ) {
        Text(
            "Storage",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        val freeBytes = remember { android.os.StatFs(
            android.os.Environment.getDataDirectory().path
        ).availableBytes }
        val total = (usedBytes + freeBytes).coerceAtLeast(1L)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            Text(
                formatBytes(usedBytes) + " used",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatBytes(freeBytes) + " free",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.Secondary
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(AppColors.SurfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth((usedBytes.toFloat() / total).coerceIn(0.01f, 1f))
                    .height(6.dp)
                    .background(AppColors.Accent)
            )
        }

        HorizontalDivider(color = AppColors.SurfaceVariant, modifier = Modifier.padding(top = 12.dp))

        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Downloaded songs")
                Text(
                    "${tracks.size} tracks available offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Secondary
                )
            }
        }

        HorizontalDivider(color = AppColors.SurfaceVariant)

        Text(
            "Clear downloads",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { confirmClear = true }
                .padding(16.dp)
        )

        Text(
            "Downloads play from the device, so they keep working with no " +
                "connection to the server.",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.Secondary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1) return "%.2f GB".format(gb)
    val mb = bytes / 1_000_000.0
    return "%.0f MB".format(mb)
}
