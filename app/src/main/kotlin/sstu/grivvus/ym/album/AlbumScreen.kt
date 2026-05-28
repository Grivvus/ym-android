package sstu.grivvus.ym.album

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material.icons.sharp.Edit
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.music.UploadTrackModal
import sstu.grivvus.ym.music.queryDisplayNameWithoutExtension
import sstu.grivvus.ym.playback.PlaybackViewModel
import sstu.grivvus.ym.library.LibraryTrackItemUi
import sstu.grivvus.ym.ui.resolve
import sstu.grivvus.ym.ui.theme.appIcons

private data class UploadTrackModalRequest(
    val sessionId: Long,
    val uri: Uri,
    val initialTitle: String,
    val artistId: Long,
    val artistName: String,
    val albumId: Long,
    val albumName: String,
)

private data class EditAlbumDraft(
    val name: String,
    val releaseYear: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    onOpenPlayer: (Long) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit = {},
    viewModel: AlbumViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val album = uiState.album
    val pendingRemoveTrackIds = uiState.pendingRemoveTrackIds
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTracksDialog by remember { mutableStateOf(false) }
    var uploadTrackRequest by remember { mutableStateOf<UploadTrackModalRequest?>(null) }
    var editAlbumDraft by remember { mutableStateOf<EditAlbumDraft?>(null) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAlbumCover(uri)
        }
    }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val currentAlbum = album ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            uploadTrackRequest = UploadTrackModalRequest(
                sessionId = System.nanoTime(),
                uri = uri,
                initialTitle = context.queryDisplayNameWithoutExtension(uri),
                artistId = currentAlbum.artistId,
                artistName = currentAlbum.artistName.resolve(context),
                albumId = currentAlbum.id,
                albumName = currentAlbum.name.resolve(context),
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                AlbumScreenEvent.NavigateBack -> onBack()
                AlbumScreenEvent.AlbumUpdated -> {
                    editAlbumDraft = null
                }
            }
        }
    }

    BottomNavScaffold(
        navigateToMusic = navigateToMusic,
        navigateToLibrary = navigateToLibrary,
        navigateToProfile = navigateToProfile,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isSelectionMode) {
                            pluralStringResource(
                                R.plurals.selected_count,
                                uiState.selectedTrackIds.size,
                                uiState.selectedTrackIds.size,
                            )
                        } else {
                            ""
                        },
                    )
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        TextButton(onClick = viewModel::clearSelection) {
                            Text(stringResource(R.string.common_action_cancel))
                        }
                    } else {
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.common_action_back))
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        TextButton(
                            onClick = viewModel::requestRemoveSelectedTracks,
                            enabled = !uiState.isMutating,
                        ) {
                            Text(stringResource(R.string.common_action_delete))
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.refresh() },
                            enabled = !uiState.isMutating,
                        ) {
                            Icon(
                                appIcons.Sync,
                                contentDescription = stringResource(R.string.common_cd_fetch_data_from_server),
                            )
                        }
                        if (album?.canEdit == true) {
                            IconButton(
                                onClick = {
                                    editAlbumDraft = EditAlbumDraft(
                                        name = album.name.resolve(context),
                                        releaseYear = album.releaseYear?.toString().orEmpty(),
                                    )
                                },
                                enabled = !uiState.isMutating,
                            ) {
                                Icon(
                                    appIcons.Edit,
                                    contentDescription = stringResource(R.string.common_action_edit),
                                )
                            }
                        }
                        if (album != null) {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                enabled = !uiState.isMutating,
                            ) {
                                Icon(
                                    appIcons.Delete,
                                    contentDescription = stringResource(R.string.common_action_delete),
                                )
                            }
                        }
                    }
                },
            )
        },
        miniPlayer = miniPlayer,
    ) { innerPadding ->
        ScreenStateHost(
            isLoading = uiState.isLoading && album == null,
            errorMessage = if (editAlbumDraft != null) null else uiState.errorMessage,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.padding(innerPadding),
        ) {
            if (album != null) {
                AlbumDetails(
                    album = album,
                    isBusy = uiState.isRefreshing || uiState.isMutating,
                    selectedTrackIds = uiState.selectedTrackIds,
                    isSelectionMode = uiState.isSelectionMode,
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
                    onSelectCover = { coverPicker.launch("image/*") },
                    onTrackClick = { trackId ->
                        if (uiState.isSelectionMode) {
                            viewModel.toggleTrackSelection(trackId)
                        } else {
                            viewModel.playbackQueueFor(trackId)?.let { queue ->
                                playbackViewModel.play(queue)
                                onOpenPlayer(trackId)
                            }
                        }
                    },
                    onTrackLongClick = viewModel::onTrackLongPress,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyStateCard(
                        title = stringResource(R.string.album_unavailable_title),
                        description = stringResource(R.string.album_unavailable_description),
                    )
                }
            }
        }
    }

    editAlbumDraft?.let { draft ->
        EditAlbumDialog(
            draft = draft,
            isBusy = uiState.isMutating,
            errorMessage = uiState.errorMessage?.resolve(),
            onDismiss = {
                editAlbumDraft = null
                viewModel.dismissError()
            },
            onDismissError = viewModel::dismissError,
            onNameChange = { value ->
                editAlbumDraft = draft.copy(name = value)
            },
            onReleaseYearChange = { value ->
                editAlbumDraft = draft.copy(releaseYear = value)
            },
            onConfirm = {
                viewModel.updateAlbumMetadata(
                    name = draft.name,
                    releaseYearInput = draft.releaseYear,
                )
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.album_delete_title)) },
            text = { Text(stringResource(R.string.album_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAlbum()
                    },
                    enabled = !uiState.isMutating,
                ) {
                    Text(stringResource(R.string.common_action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !uiState.isMutating,
                ) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    if (pendingRemoveTrackIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveTracksDialog,
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.album_remove_tracks_title,
                        pendingRemoveTrackIds.size,
                        pendingRemoveTrackIds.size,
                    ),
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.album_remove_tracks_message,
                        pendingRemoveTrackIds.size,
                        pendingRemoveTrackIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::removePendingTracksFromAlbum,
                    enabled = !uiState.isMutating,
                ) {
                    Text(stringResource(R.string.common_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveTracksDialog) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    if (showAddTracksDialog && album != null && album.canRemoveTracks) {
        val selectedIds = album.tracks.map { it.id }.toSet()
        AddTracksDialog(
            tracks = uiState.libraryTracks.filterNot { it.id in selectedIds },
            isBusy = uiState.isMutating,
            onDismiss = { showAddTracksDialog = false },
            onConfirm = { trackIds ->
                viewModel.addTracksToAlbum(trackIds)
                showAddTracksDialog = false
            },
        )
    }

    uploadTrackRequest?.let { request ->
        UploadTrackModal(
            sessionId = request.sessionId,
            playlistId = null,
            uri = request.uri,
            initialTitle = request.initialTitle,
            initialArtistId = request.artistId,
            initialArtistName = request.artistName,
            initialAlbumId = request.albumId,
            initialAlbumName = request.albumName,
            isAlbumContextLocked = true,
            onDismiss = { uploadTrackRequest = null },
            onUploadSuccess = {
                uploadTrackRequest = null
                viewModel.reloadFromLocal()
            },
        )
    }
}

@Composable
private fun EditAlbumDialog(
    draft: EditAlbumDraft,
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit,
    onNameChange: (String) -> Unit,
    onReleaseYearChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { value ->
                        onDismissError()
                        onNameChange(value)
                    },
                    label = { Text(stringResource(R.string.common_label_album_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.releaseYear,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all(Char::isDigit)) {
                            onDismissError()
                            onReleaseYearChange(value)
                        }
                    },
                    label = { Text(stringResource(R.string.common_label_release_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                ErrorTooltip(
                    message = errorMessage.orEmpty(),
                    visible = errorMessage != null,
                    onDismiss = onDismissError,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.name.isNotBlank() && !isBusy,
            ) {
                Text(stringResource(R.string.common_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

@Composable
private fun AlbumDetails(
    album: AlbumDetailUi,
    isBusy: Boolean,
    selectedTrackIds: Set<Long>,
    isSelectionMode: Boolean,
    onPlayAll: () -> Unit,
    onAddExistingTrack: () -> Unit,
    onUploadTrack: () -> Unit,
    onSelectCover: () -> Unit,
    onTrackClick: (Long) -> Unit,
    onTrackLongClick: (Long) -> Unit,
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
                        uri = album.coverUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable(enabled = !isBusy, onClick = onSelectCover),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = album.name.resolve(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = album.artistName.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val albumMetadataText = listOfNotNull(
                        album.releaseYear?.toString(),
                        pluralStringResource(
                            R.plurals.track_count,
                            album.tracks.size,
                            album.tracks.size,
                        ),
                    ).joinToString(" • ")
                    Text(
                        text = albumMetadataText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onPlayAll,
                        enabled = album.tracks.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.common_action_play_all))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (album.canRemoveTracks) {
                            FilledTonalButton(
                                onClick = onAddExistingTrack,
                                enabled = !isBusy,
                            ) {
                                Text(stringResource(R.string.common_action_add_from_library))
                            }
                        }
                        Button(onClick = onUploadTrack, enabled = !isBusy) {
                            Text(stringResource(R.string.common_action_upload_track))
                        }
                    }
                    if (isBusy) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.album_status_applying_changes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.common_title_tracks),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (album.tracks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = stringResource(R.string.album_empty_title),
                    description = stringResource(R.string.album_empty_description),
                )
            }
        } else {
            items(album.tracks, key = { it.id }) { track ->
                AlbumTrackRow(
                    track = track,
                    isSelected = track.id in selectedTrackIds,
                    isSelectionMode = isSelectionMode,
                    isBusy = isBusy,
                    onClick = { onTrackClick(track.id) },
                    onLongClick = { onTrackLongClick(track.id) },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun AddTracksDialog(
    tracks: List<LibraryTrackItemUi>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var selectedIds by remember(tracks) { mutableStateOf(emptySet<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_add_tracks_title)) },
        text = {
            if (tracks.isEmpty()) {
                Text(stringResource(R.string.album_all_library_tracks_added))
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
                                    text = track.subtitle.resolve(),
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
                Text(stringResource(R.string.common_action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTrackRow(
    track: LibraryTrackItemUi,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = !isBusy,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (isSelected) 6.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    enabled = !isBusy,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (isSelectionMode) 8.dp else 0.dp),
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.subtitle.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
