package sstu.grivvus.ym.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.components.BottomBar
import sstu.grivvus.ym.components.ErrorSnackbar
import sstu.grivvus.ym.playback.PlaybackViewModel
import sstu.grivvus.ym.ui.theme.appIcons

private data class RenamePlaylistDraft(
    val value: String,
)

private data class UploadTrackModalRequest(
    val sessionId: Long,
    val playlistId: Long,
    val uri: Uri,
    val initialTitle: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    onOpenPlayer: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playlist = uiState.playlist

    var renameDraft by remember { mutableStateOf<RenamePlaylistDraft?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTracksDialog by remember { mutableStateOf(false) }
    var uploadTrackRequest by remember { mutableStateOf<UploadTrackModalRequest?>(null) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.uploadPlaylistCover(uri)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val playlistId = playlist?.id ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            uploadTrackRequest = UploadTrackModalRequest(
                sessionId = System.nanoTime(),
                playlistId = playlistId,
                uri = uri,
                initialTitle = context.queryDisplayName(uri),
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                PlaylistScreenEvent.NavigateBack -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(playlist?.name ?: "Playlist")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(appIcons.Sync, contentDescription = "fetch data from server")
                    }
                    if (playlist != null) {
                        TextButton(
                            onClick = {
                                renameDraft = RenamePlaylistDraft(value = playlist.name)
                            },
                        ) {
                            Text("Rename")
                        }
                        TextButton(onClick = { coverPicker.launch("image/*") }) {
                            Text("Cover")
                        }
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("Delete")
                        }
                    }
                },
            )
        },
        bottomBar = {
            BottomBar(
                onMusicClick = navigateToMusic,
                onLibraryClick = navigateToLibrary,
                onProfileClick = navigateToProfile,
            )
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading && playlist == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                playlist != null -> {
                    PlaylistDetails(
                        playlist = playlist,
                        isBusy = uiState.isMutating || uiState.isRefreshing,
                        onPlayAll = {
                            viewModel.playbackQueueFromStart()?.let { queue ->
                                playbackViewModel.play(queue)
                                val trackId = queue.items.getOrNull(queue.startIndex)?.id
                                    ?: return@let
                                onOpenPlayer(trackId)
                            }
                        },
                        onAddExistingTrack = { showAddTracksDialog = true },
                        onUploadTrack = { audioPicker.launch("audio/*") },
                        onTrackClick = { trackId ->
                            viewModel.playbackQueueFor(trackId)?.let { queue ->
                                playbackViewModel.play(queue)
                                onOpenPlayer(trackId)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        EmptyStateCard(
                            title = "Playlist unavailable",
                            description = "This playlist could not be loaded. Go back to the list and refresh.",
                        )
                    }
                }
            }

            ErrorSnackbar(
                errorMessage = uiState.errorMessage,
                onDismiss = viewModel::dismissError,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    renameDraft?.let { draft ->
        RenamePlaylistDialog(
            draft = draft,
            isBusy = uiState.isMutating,
            onDismiss = { renameDraft = null },
            onValueChange = { value -> renameDraft = draft.copy(value = value) },
            onConfirm = {
                viewModel.renamePlaylist(draft.value.trim())
                renameDraft = null
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete playlist") },
            text = { Text("Playlist will be removed from the server and local storage.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist()
                        showDeleteDialog = false
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showAddTracksDialog && playlist != null) {
        val selectedIds = playlist.tracks.map { it.id }.toSet()
        AddTracksDialog(
            tracks = uiState.libraryTracks.filterNot { it.id in selectedIds },
            isBusy = uiState.isMutating,
            onDismiss = { showAddTracksDialog = false },
            onConfirm = { trackIds ->
                viewModel.addTracksToPlaylist(trackIds)
                showAddTracksDialog = false
            },
        )
    }

    uploadTrackRequest?.let { request ->
        UploadTrackModal(
            sessionId = request.sessionId,
            playlistId = request.playlistId,
            uri = request.uri,
            initialTitle = request.initialTitle,
            onDismiss = { uploadTrackRequest = null },
            onUploadSuccess = {
                uploadTrackRequest = null
                viewModel.reloadFromLocal()
            },
        )
    }
}

@Composable
private fun PlaylistDetails(
    playlist: PlaylistDetailUi,
    isBusy: Boolean,
    onPlayAll: () -> Unit,
    onAddExistingTrack: () -> Unit,
    onUploadTrack: () -> Unit,
    onTrackClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Artwork(
                        uri = playlist.coverUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${playlist.tracks.size} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = onPlayAll,
                            enabled = playlist.tracks.isNotEmpty(),
                        ) {
                            Text("Play all")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onAddExistingTrack) {
                            Text("Add from library")
                        }
                        Button(onClick = onUploadTrack) {
                            Text("Upload track")
                        }
                    }
                    if (isBusy) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Applying changes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Tracks",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (playlist.tracks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "Playlist is empty",
                    description = "Add tracks from the library or upload new ones directly here.",
                )
            }
        } else {
            items(playlist.tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(track.id) },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackItemUi,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                uri = track.coverUri,
                modifier = Modifier.size(56.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RenamePlaylistDialog(
    draft: RenamePlaylistDraft,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = draft.value,
                onValueChange = onValueChange,
                label = { Text("Playlist name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.value.isNotBlank() && !isBusy,
            ) {
                Text("Save")
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
private fun AddTracksDialog(
    tracks: List<TrackItemUi>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selectedIds by remember(tracks) { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tracks") },
        text = {
            if (tracks.isEmpty()) {
                Text("All library tracks are already in this playlist.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tracks, key = { it.id }) { track ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds =
                                        if (track.id in selectedIds) selectedIds - track.id
                                        else selectedIds + track.id
                                },
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = if (track.id in selectedIds) 4.dp else 0.dp,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = track.name,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = track.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds) },
                enabled = selectedIds.isNotEmpty() && !isBusy,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
