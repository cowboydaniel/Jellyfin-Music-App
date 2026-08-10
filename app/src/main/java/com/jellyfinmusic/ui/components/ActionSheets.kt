package com.jellyfinmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellyfinmusic.data.ActionSheet
import com.jellyfinmusic.network.BaseItem
import com.jellyfinmusic.ui.ActionsViewModel
import com.jellyfinmusic.ui.theme.AppColors

/**
 * Hosts every context sheet in one place, driven by the shared
 * [com.jellyfinmusic.data.ActionsController]. Mounted once, near the root.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ActionSheetHost(
    onShowMessage: (String) -> Unit,
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(toast) {
        toast?.let {
            onShowMessage(it)
            viewModel.actions.consumeToast()
        }
    }

    if (sheet is ActionSheet.None) return

    ModalBottomSheet(
        onDismissRequest = viewModel.actions::dismissSheet,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        dragHandle = null
    ) {
        when (val current = sheet) {
            is ActionSheet.TrackMenu -> TrackMenuSheet(
                item = current.item,
                isFavorite = current.item.id in favorites,
                canRemoveFromPlaylist = current.playlistContext != null,
                artworkUrl = viewModel.imageUrl(current.item),
                onPlayNext = {
                    viewModel.actions.playNext(current.item)
                    viewModel.actions.dismissSheet()
                },
                onAddToQueue = {
                    viewModel.actions.addToQueue(current.item)
                    viewModel.actions.dismissSheet()
                },
                onToggleFavorite = {
                    viewModel.actions.toggleFavorite(current.item)
                    viewModel.actions.dismissSheet()
                },
                onAddToPlaylist = { viewModel.actions.showAddToPlaylist(current.item) },
                onRemoveFromPlaylist = {
                    current.playlistContext?.let(viewModel.actions::removeFromPlaylist)
                }
            )

            is ActionSheet.AddToPlaylist -> {
                val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                AddToPlaylistSheet(
                    playlists = playlists,
                    artworkFor = viewModel::imageUrl,
                    onCreateNew = { viewModel.actions.showCreatePlaylist(current.item) },
                    onSelect = { viewModel.actions.addToPlaylist(it, current.item) }
                )
            }

            is ActionSheet.CreatePlaylist -> CreatePlaylistSheet(
                onCreate = { viewModel.actions.createPlaylist(it, current.seedItem) },
                onCancel = viewModel.actions::dismissSheet
            )

            ActionSheet.None -> Unit
        }
    }
}

@Composable
private fun TrackMenuSheet(
    item: BaseItem,
    isFavorite: Boolean,
    canRemoveFromPlaylist: Boolean,
    artworkUrl: String?,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(artworkUrl, Modifier.size(48.dp), shape = RoundedCornerShape(4.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(item.name.orEmpty(), maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                Text(
                    item.artistName.orEmpty(),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Secondary
                )
            }
        }
        HorizontalDivider(color = AppColors.SurfaceVariant)

        SheetAction(
            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (isFavorite) "Remove from Liked songs" else "Save to Liked songs",
            tint = if (isFavorite) AppColors.Accent else AppColors.OnBackground,
            onClick = onToggleFavorite
        )
        SheetAction(Icons.AutoMirrored.Filled.PlaylistPlay, "Play next", onClick = onPlayNext)
        SheetAction(Icons.AutoMirrored.Filled.QueueMusic, "Add to queue", onClick = onAddToQueue)
        SheetAction(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", onClick = onAddToPlaylist)
        if (canRemoveFromPlaylist) {
            SheetAction(
                Icons.Filled.Delete,
                "Remove from this playlist",
                onClick = onRemoveFromPlaylist
            )
        }
    }
}

@Composable
private fun AddToPlaylistSheet(
    playlists: List<BaseItem>,
    artworkFor: (BaseItem) -> String?,
    onCreateNew: () -> Unit,
    onSelect: (BaseItem) -> Unit
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            "Add to playlist",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        SheetAction(Icons.Filled.Add, "New playlist", onClick = onCreateNew)
        HorizontalDivider(color = AppColors.SurfaceVariant)

        if (playlists.isEmpty()) {
            Text(
                "No playlists yet.",
                color = AppColors.Secondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(playlist) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(
                            artworkFor(playlist),
                            Modifier.size(44.dp),
                            shape = RoundedCornerShape(4.dp)
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(playlist.name.orEmpty(), maxLines = 1)
                            playlist.childCount?.let {
                                Text(
                                    "$it tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.Secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistSheet(
    onCreate: (String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(Modifier.padding(16.dp)) {
        Text("New playlist", style = MaterialTheme.typography.titleMedium)
        TextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("Playlist name", color = AppColors.Secondary) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate(name) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.SurfaceVariant,
                unfocusedContainerColor = AppColors.SurfaceVariant,
                focusedIndicatorColor = AppColors.Accent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .focusRequester(focusRequester)
        )
        Row(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            Text(
                "Cancel",
                color = AppColors.Secondary,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(12.dp)
            )
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.OnBackground,
                    contentColor = AppColors.Background
                )
            ) {
                Text("Create")
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AppColors.OnBackground
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
