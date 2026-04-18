package sstu.grivvus.ym.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.ui.theme.YMTheme
import sstu.grivvus.ym.ui.theme.appIcons

private data class CreatePlaylistDraft(
    val name: String = "",
    val coverUri: Uri? = null,
    val isPublic: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    navigateToPlaylist: (Long) -> Unit,
    refreshToken: Long,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var createDraft by remember { mutableStateOf(CreatePlaylistDraft()) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        createDraft = createDraft.copy(coverUri = uri)
    }

    LaunchedEffect(refreshToken) {
        if (refreshToken != 0L) {
            viewModel.refresh()
        }
    }

    YMTheme {
        BottomNavScaffold(
            navigateToMusic = navigateToMusic,
            navigateToLibrary = navigateToLibrary,
            navigateToProfile = navigateToProfile,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Playlists")
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(appIcons.Sync, contentDescription = "fetch data from server")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Text("+")
                }
            },
        ) { innerPadding ->
            ScreenStateHost(
                isLoading = uiState.isLoading && uiState.playlists.isEmpty(),
                errorMessage = uiState.errorMessage,
                onDismissError = viewModel::dismissError,
                modifier = Modifier.padding(innerPadding),
            ) {
                PlaylistOverview(
                    playlists = uiState.playlists,
                    isBusy = uiState.isMutating || uiState.isRefreshing,
                    onPlaylistClick = navigateToPlaylist,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showCreateDialog) {
            CreatePlaylistDialog(
                draft = createDraft,
                isBusy = uiState.isMutating,
                onDismiss = {
                    showCreateDialog = false
                    createDraft = CreatePlaylistDraft()
                },
                onNameChange = { value -> createDraft = createDraft.copy(name = value) },
                onVisibilityChange = { isPublic ->
                    createDraft = createDraft.copy(isPublic = isPublic)
                },
                onSelectCover = { coverPicker.launch("image/*") },
                onConfirm = {
                    viewModel.createPlaylist(
                        name = createDraft.name.trim(),
                        coverUri = createDraft.coverUri,
                        isPublic = createDraft.isPublic,
                    )
                    showCreateDialog = false
                    createDraft = CreatePlaylistDraft()
                },
            )
        }
    }
}

@Composable
private fun PlaylistOverview(
    playlists: List<PlaylistListItemUi>,
    isBusy: Boolean,
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                Text(
                    text = "Your playlists",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isBusy) {
                        "Updating library..."
                    } else {
                        "Open a playlist to manage tracks and cover art."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (playlists.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No playlists yet",
                    description = "Create the first playlist from the floating action button.",
                )
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(playlist.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            uri = playlist.coverUri,
                            modifier = Modifier.size(72.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Open",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    draft: CreatePlaylistDraft,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
    onSelectCover: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text("Playlist name") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = draft.isPublic,
                        onCheckedChange = onVisibilityChange,
                    )
                    Text(
                        text = "Public playlist",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Artwork(
                    uri = draft.coverUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                )
                TextButton(onClick = onSelectCover) {
                    Text("Select cover")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.name.isNotBlank() && !isBusy,
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun UploadScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Upload route is no longer used", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
fun PlayerPlaceholderScreen(
    trackId: Long,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Player screen is not implemented yet", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Track ID: $trackId")
        Spacer(modifier = Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onBack) {
            Text("Back")
        }
    }
}
