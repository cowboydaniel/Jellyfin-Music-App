package com.jellyfinmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.ui.LibraryStatus
import com.jellyfinmusic.ui.SearchMode
import com.jellyfinmusic.ui.SearchViewModel
import com.jellyfinmusic.ui.components.MediaRow

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshConfigFlag() }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search artists and albums") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = viewModel::search) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.onModeChange(mode) },
                    label = { Text(if (mode == SearchMode.ARTISTS) "Artists" else "Albums") }
                )
            }
        }

        if (!state.lidarrConfigured) {
            Text(
                "Add your Lidarr server URL and API key in Settings to search and request music.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        state.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (state.isLoading) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
        ) {
            items(state.results, key = { it.key }) { result ->
                MediaRow(
                    title = result.title,
                    subtitle = result.subtitle,
                    artworkUrl = result.imageUrl,
                    trailing = {
                        when (result.status) {
                            LibraryStatus.IN_LIBRARY -> AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("In library") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )

                            LibraryStatus.REQUESTED -> AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("Requested") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.HourglassTop,
                                        contentDescription = null,
                                        Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )

                            LibraryStatus.NOT_IN_LIBRARY -> AssistChip(
                                onClick = { viewModel.request(result) },
                                label = { Text("Request") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
