package sstu.grivvus.ym.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.music.UploadTrackModal
import sstu.grivvus.ym.music.queryDisplayName
import sstu.grivvus.ym.ui.theme.YMTheme
import sstu.grivvus.ym.ui.theme.appIcons

private data class UploadTrackModalRequest(
    val sessionId: Long,
    val uri: Uri,
    val initialTitle: String,
)

private data class RestoreArchiveRequest(
    val uri: Uri,
    val displayName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    navigateToArtist: (Long) -> Unit,
    navigateToAlbum: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val restoreStatus = uiState.restoreStatus
    val isRestoreRunning =
        restoreStatus != null && !restoreStatus.isFinished && !restoreStatus.isFailed
    val isArchiveOperationInProgress =
        uiState.isCreatingBackup || uiState.isSavingBackup || uiState.isStartingRestore
    val pendingDeleteTrackIds = uiState.pendingDeleteTrackIds
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var uploadTrackRequest by remember { mutableStateOf<UploadTrackModalRequest?>(null) }
    var pendingRestoreRequest by remember { mutableStateOf<RestoreArchiveRequest?>(null) }

    val backupSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        viewModel.saveBackupTo(uri)
    }
    val restoreArchivePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreRequest = RestoreArchiveRequest(
                uri = uri,
                displayName = context.queryDisplayName(uri),
            )
        }
    }
    val trackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            uploadTrackRequest = UploadTrackModalRequest(
                sessionId = System.nanoTime(),
                uri = uri,
                initialTitle = context.queryDisplayName(uri),
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LibraryScreenEvent.RequestBackupSaveLocation -> {
                    backupSaveLauncher.launch(event.suggestedFileName)
                }

                is LibraryScreenEvent.NavigateToAlbum -> navigateToAlbum(event.albumId)
                is LibraryScreenEvent.NavigateToArtist -> navigateToArtist(event.artistId)
            }
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
                        Text(
                            if (uiState.isSelectionMode) {
                                "${uiState.selectedTrackIds.size} selected"
                            } else {
                                "Library"
                            },
                        )
                    },
                    navigationIcon = {
                        if (uiState.isSelectionMode) {
                            TextButton(onClick = viewModel::clearSelection) {
                                Text("Cancel")
                            }
                        }
                    },
                    actions = {
                        if (uiState.isSelectionMode) {
                            TextButton(
                                onClick = viewModel::requestDeleteSelected,
                                enabled = !uiState.isTrackMutating,
                            ) {
                                Text("Delete")
                            }
                        } else {
                            IconButton(onClick = { viewModel.refresh() }) {
                                Icon(appIcons.Sync, contentDescription = "fetch data from server")
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (!uiState.isSelectionMode) {
                    ExtendedFloatingActionButton(
                        onClick = { trackPicker.launch("audio/*") },
                        expanded = true,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                            )
                        },
                        text = {
                            Text("Add track")
                        },
                    )
                }
            },
        ) { innerPadding ->
            ScreenStateHost(
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                onDismissError = viewModel::dismissError,
                modifier = Modifier.padding(innerPadding),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    uiState.infoMessage?.let { infoMessage ->
                        item {
                            InfoCard(
                                message = infoMessage,
                                onDismiss = viewModel::dismissInfo,
                            )
                        }
                    }

                    if (uiState.isSuperuser) {
                        item {
                            BackupRestoreActionsCard(
                                isBusy = isArchiveOperationInProgress,
                                isRestoreRunning = isRestoreRunning,
                                onCreateBackupClick = { showBackupDialog = true },
                                onRestoreClick = {
                                    restoreArchivePicker.launch(
                                        arrayOf(
                                            "application/zip",
                                            "application/x-zip-compressed",
                                            "application/octet-stream",
                                        ),
                                    )
                                },
                            )
                        }
                        restoreStatus
                            ?.takeIf { status -> !status.isFinished || status.isFailed }
                            ?.let { status ->
                                item {
                                    RestoreStatusBanner(status = status)
                                }
                            }
                    }

                    item {
                        TrackSectionHeader()
                    }

                    if (uiState.tracks.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No tracks yet",
                                description = "Upload tracks from local storage to populate the library.",
                            )
                        }
                    } else {
                        items(uiState.tracks, key = { it.id }) { track ->
                            LibraryTrackRow(
                                track = track,
                                isSelected = track.id in uiState.selectedTrackIds,
                                isSelectionMode = uiState.isSelectionMode,
                                isBusy = uiState.isTrackMutating,
                                onClick = { viewModel.onTrackClick(track.id) },
                                onLongClick = { viewModel.onTrackLongPress(track.id) },
                                onDelete = { viewModel.requestDeleteTrack(track.id) },
                                onGoToArtist = { viewModel.openArtist(track.id) },
                                onGoToAlbum = { viewModel.openAlbum(track.id) },
                            )
                        }
                    }
                }
            }
        }

        if (pendingDeleteTrackIds.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDeleteDialog,
                title = {
                    Text(
                        if (pendingDeleteTrackIds.size == 1) {
                            "Delete track"
                        } else {
                            "Delete tracks"
                        },
                    )
                },
                text = {
                    Text(
                        if (pendingDeleteTrackIds.size == 1) {
                            "The selected track will be removed from the server and local storage."
                        } else {
                            "The selected ${pendingDeleteTrackIds.size} tracks will be removed from the server and local storage."
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::deletePendingTracks,
                        enabled = !uiState.isTrackMutating,
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDeleteDialog) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showBackupDialog) {
            BackupOptionsDialog(
                includeImages = uiState.includeImages,
                includeTranscodedTracks = uiState.includeTranscodedTracks,
                isBusy = isArchiveOperationInProgress || isRestoreRunning,
                onDismiss = { showBackupDialog = false },
                onIncludeImagesChange = viewModel::changeIncludeImages,
                onIncludeTranscodedTracksChange = viewModel::changeIncludeTranscodedTracks,
                onConfirm = {
                    showBackupDialog = false
                    viewModel.createBackup()
                },
            )
        }

        pendingRestoreRequest?.let { request ->
            RestoreConfirmationDialog(
                archiveName = request.displayName,
                isBusy = isArchiveOperationInProgress || isRestoreRunning,
                onDismiss = { pendingRestoreRequest = null },
                onConfirm = {
                    pendingRestoreRequest = null
                    viewModel.restoreFromArchive(request.uri)
                },
            )
        }

        uploadTrackRequest?.let { request ->
            UploadTrackModal(
                sessionId = request.sessionId,
                playlistId = null,
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
}

@Composable
private fun TrackSectionHeader() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.error,
        thickness = 5.dp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTrackRow(
    track: LibraryTrackItemUi,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
) {
    var menuExpanded by rememberSaveable(track.id) { mutableStateOf(false) }
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
                    text = track.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    enabled = !isBusy,
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track actions",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Go to artist") },
                        onClick = {
                            menuExpanded = false
                            onGoToArtist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Go to album") },
                        enabled = track.albumId != null,
                        onClick = {
                            menuExpanded = false
                            onGoToAlbum()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupRestoreActionsCard(
    isBusy: Boolean,
    isRestoreRunning: Boolean,
    onCreateBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Backup & restore",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isRestoreRunning) {
                    "Restore is running. New archive actions are temporarily disabled."
                } else if (isBusy) {
                    "Archive operation in progress."
                } else {
                    "Export a backup archive or restore library data from an existing archive."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onCreateBackupClick,
                    enabled = !isBusy && !isRestoreRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Create backup")
                }
                Button(
                    onClick = onRestoreClick,
                    enabled = !isBusy && !isRestoreRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (isRestoreRunning) {
                            "Restoring..."
                        } else {
                            "Restore"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupOptionsDialog(
    includeImages: Boolean,
    includeTranscodedTracks: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onIncludeImagesChange: (Boolean) -> Unit,
    onIncludeTranscodedTracksChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Choose what to include in the archive before saving it to the selected location.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BackupOptionRow(
                    title = "Include images",
                    checked = includeImages,
                    onCheckedChange = onIncludeImagesChange,
                    enabled = !isBusy,
                )
                BackupOptionRow(
                    title = "Include transcoded tracks",
                    checked = includeTranscodedTracks,
                    onCheckedChange = onIncludeTranscodedTracksChange,
                    enabled = !isBusy,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBusy,
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun BackupOptionRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RestoreConfirmationDialog(
    archiveName: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Start restore from the selected archive?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = archiveName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBusy,
            ) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RestoreStatusBanner(status: RestoreStatusUi) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = status.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Restore ID: ${status.restoreId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!status.isFinished && !status.isFailed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = "The app is polling the server for the latest status.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            status.errorMessage?.takeIf { it.isNotBlank() }?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(
    message: String,
    onDismiss: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Dismiss")
            }
        }
    }
}
