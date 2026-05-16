package sstu.grivvus.ym.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.sharp.Delete
import androidx.compose.material.icons.sharp.Edit
import androidx.compose.material.icons.sharp.Share
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.music.UploadTrackModal
import sstu.grivvus.ym.music.queryDisplayNameWithoutExtension
import sstu.grivvus.ym.playback.PlaybackViewModel
import sstu.grivvus.ym.ui.resolve
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
    miniPlayer: @Composable () -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playlist = uiState.playlist

    var renameDraft by remember { mutableStateOf<RenamePlaylistDraft?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSharingDialog by remember { mutableStateOf(false) }
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
                initialTitle = context.queryDisplayNameWithoutExtension(uri),
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

    BottomNavScaffold(
        navigateToMusic = navigateToMusic,
        navigateToLibrary = navigateToLibrary,
        navigateToProfile = navigateToProfile,
        topBar = {
            TopAppBar(
                title = {
                    Text("")
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            appIcons.Sync,
                            contentDescription = stringResource(R.string.common_cd_fetch_data_from_server),
                        )
                    }
                    if (playlist != null) {
                        if (playlist.canDelete) {
                            IconButton(
                                onClick = {
                                    showSharingDialog = true
                                    viewModel.loadSharingInfo()
                                },
                            ) {
                                Icon(
                                    appIcons.Share,
                                    contentDescription = stringResource(
                                        R.string.playlist_cd_manage_access,
                                    ),
                                )
                            }
                        }
                        if (playlist.canDelete) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(
                                    appIcons.Delete,
                                    contentDescription = stringResource(
                                        R.string.common_action_delete,
                                    ),
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
            isLoading = uiState.isLoading && playlist == null,
            errorMessage = uiState.errorMessage,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.padding(innerPadding),
        ) {
            if (playlist != null) {
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
                    onRename = {
                        renameDraft = RenamePlaylistDraft(value = playlist.name)
                    },
                    onSelectCover = { coverPicker.launch("image/*") },
                    onTrackClick = { trackId ->
                        viewModel.playbackQueueFor(trackId)?.let { queue ->
                            playbackViewModel.play(queue)
                            onOpenPlayer(trackId)
                        }
                    },
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
                        title = stringResource(R.string.playlist_unavailable_title),
                        description = stringResource(R.string.playlist_unavailable_description),
                    )
                }
            }
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
            title = { Text(stringResource(R.string.playlist_delete_title)) },
            text = { Text(stringResource(R.string.playlist_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist()
                        showDeleteDialog = false
                    },
                ) {
                    Text(stringResource(R.string.common_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    if (showSharingDialog && playlist != null) {
        PlaylistSharingDialog(
            sharing = uiState.sharing,
            onDismiss = { showSharingDialog = false },
            onToggleUser = viewModel::toggleSharingUserSelection,
            onWritePermissionChange = viewModel::setSharingWritePermission,
            onShare = viewModel::shareWithSelectedUsers,
            onRevoke = viewModel::revokeUserAccess,
        )
    }

    if (showAddTracksDialog && playlist != null && playlist.canEdit) {
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
private fun PlaylistSharingDialog(
    sharing: PlaylistSharingUiState,
    onDismiss: () -> Unit,
    onToggleUser: (Long) -> Unit,
    onWritePermissionChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onRevoke: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_share_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sharing.isLoading) {
                    Text(
                        text = stringResource(R.string.playlist_share_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SharingSectionTitle(text = stringResource(R.string.playlist_share_existing))
                    if (sharing.sharedUsers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.playlist_share_empty_shared),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        sharing.sharedUsers.forEach { user ->
                            SharedUserRow(
                                user = user,
                                isBusy = sharing.isMutating,
                                onRevoke = onRevoke,
                            )
                        }
                    }

                    SharingSectionTitle(text = stringResource(R.string.playlist_share_available))
                    if (sharing.availableUsers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.playlist_share_empty_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        sharing.availableUsers.forEach { user ->
                            AvailableUserRow(
                                user = user,
                                selected = user.id in sharing.selectedUserIds,
                                enabled = !sharing.isMutating,
                                onToggleUser = onToggleUser,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = sharing.hasWritePermission,
                                onCheckedChange = onWritePermissionChange,
                                enabled = !sharing.isMutating,
                            )
                            Text(
                                text = stringResource(R.string.playlist_share_allow_edit),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onShare,
                enabled = sharing.selectedUserIds.isNotEmpty() &&
                        !sharing.isLoading &&
                        !sharing.isMutating,
            ) {
                Text(stringResource(R.string.playlist_share_button))
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
private fun SharingSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SharedUserRow(
    user: PlaylistSharingUserUi,
    isBusy: Boolean,
    onRevoke: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = user.username,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onRevoke(user.id) },
            enabled = !isBusy,
        ) {
            Text(stringResource(R.string.playlist_share_revoke))
        }
    }
}

@Composable
private fun AvailableUserRow(
    user: PlaylistSharingUserUi,
    selected: Boolean,
    enabled: Boolean,
    onToggleUser: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggleUser(user.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggleUser(user.id) },
            enabled = enabled,
        )
        Text(
            text = user.username,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
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
    onRename: () -> Unit,
    onSelectCover: () -> Unit,
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
                            .aspectRatio(1f)
                            .clickable(enabled = playlist.canEdit, onClick = onSelectCover),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (playlist.canEdit) {
                            IconButton(onClick = onRename) {
                                Icon(
                                    appIcons.Edit,
                                    contentDescription = stringResource(
                                        R.string.common_action_rename,
                                    ),
                                )
                            }
                        }
                    }
                    playlist.ownerUsername?.let { ownerUsername ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.playlist_owner_label, ownerUsername),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.track_count,
                            playlist.tracks.size,
                            playlist.tracks.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(
                            onClick = onPlayAll,
                            enabled = playlist.tracks.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.common_action_play_all))
                        }
                    }
                    if (playlist.canEdit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(onClick = onAddExistingTrack) {
                                Text(stringResource(R.string.common_action_add_from_library))
                            }
                            Button(onClick = onUploadTrack) {
                                Text(stringResource(R.string.common_action_upload_track))
                            }
                        }
                    }
                    if (isBusy) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.playlist_status_applying_changes),
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

        if (playlist.tracks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = stringResource(R.string.playlist_empty_title),
                    description = if (playlist.canEdit) {
                        stringResource(R.string.playlist_empty_description)
                    } else {
                        stringResource(R.string.playlist_empty_read_only_description)
                    },
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
                    text = track.subtitle.resolve(),
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
        title = { Text(stringResource(R.string.playlist_rename_title)) },
        text = {
            OutlinedTextField(
                value = draft.value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.common_label_playlist_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.value.isNotBlank() && !isBusy,
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
private fun AddTracksDialog(
    tracks: List<TrackItemUi>,
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
                Text(stringResource(R.string.playlist_all_library_tracks_added))
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
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
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
