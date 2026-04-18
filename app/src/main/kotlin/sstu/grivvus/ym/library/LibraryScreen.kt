package sstu.grivvus.ym.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
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
import sstu.grivvus.ym.components.BottomBar
import sstu.grivvus.ym.components.ErrorSnackbar
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
    var uploadTrackRequest by remember { mutableStateOf<UploadTrackModalRequest?>(null) }

    val backupSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        viewModel.saveBackupTo(uri)
    }
    val restoreArchivePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        viewModel.restoreFromArchive(uri)
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
        Scaffold(
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
            bottomBar = {
                BottomBar(
                    onMusicClick = navigateToMusic,
                    onLibraryClick = navigateToLibrary,
                    onProfileClick = navigateToProfile,
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                ) {
                                    Text(
                                        text = "Library tools",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Administrative backup and restore tools live here.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

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
                                    BackupCard(
                                        includeImages = uiState.includeImages,
                                        includeTranscodedTracks = uiState.includeTranscodedTracks,
                                        isBusy = isArchiveOperationInProgress || isRestoreRunning,
                                        isCreatingBackup = uiState.isCreatingBackup,
                                        isSavingBackup = uiState.isSavingBackup,
                                        onIncludeImagesChange = viewModel::changeIncludeImages,
                                        onIncludeTranscodedTracksChange = viewModel::changeIncludeTranscodedTracks,
                                        onCreateBackup = viewModel::createBackup,
                                    )
                                }
                                item {
                                    RestoreCard(
                                        restoreStatus = restoreStatus,
                                        isBusy = isArchiveOperationInProgress,
                                        isRestoreRunning = isRestoreRunning,
                                        onRestore = {
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
                            } else {
                                item {
                                    EmptyStateCard(
                                        title = "Administration tools unavailable",
                                        description = "Backup and restore controls are shown only to superusers.",
                                    )
                                }
                            }

                            item {
                                TrackSectionHeader(
                                    isRefreshing = uiState.isRefreshing,
                                    isBusy = uiState.isTrackMutating,
                                )
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

                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
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
private fun TrackSectionHeader(
    isRefreshing: Boolean,
    isBusy: Boolean,
) {
    Divider(
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
private fun BackupCard(
    includeImages: Boolean,
    includeTranscodedTracks: Boolean,
    isBusy: Boolean,
    isCreatingBackup: Boolean,
    isSavingBackup: Boolean,
    onIncludeImagesChange: (Boolean) -> Unit,
    onIncludeTranscodedTracksChange: (Boolean) -> Unit,
    onCreateBackup: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Create backup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Download the application backup archive to your device.",
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
            Text(
                text = if (isSavingBackup) {
                    "Saving archive to the selected location..."
                } else if (isCreatingBackup) {
                    "Preparing backup archive..."
                } else {
                    "The generated archive will be saved through the system document picker."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onCreateBackup,
                enabled = !isBusy,
            ) {
                Text("Create backup")
            }
        }
    }
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
private fun RestoreCard(
    restoreStatus: RestoreStatusUi?,
    isBusy: Boolean,
    isRestoreRunning: Boolean,
    onRestore: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Restore from backup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Upload a backup archive and track the restore status until the server finishes processing it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRestore,
                enabled = !isBusy && !isRestoreRunning,
            ) {
                Text(
                    if (isRestoreRunning) {
                        "Restore in progress"
                    } else {
                        "Select backup archive"
                    },
                )
            }
            restoreStatus?.let { status ->
                RestoreStatusCard(status = status)
            }
        }
    }
}

@Composable
private fun RestoreStatusCard(status: RestoreStatusUi) {
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
