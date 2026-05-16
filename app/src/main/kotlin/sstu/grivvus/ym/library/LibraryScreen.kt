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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
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
import androidx.compose.ui.graphics.Color
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
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.music.UploadTrackModal
import sstu.grivvus.ym.music.queryDisplayName
import sstu.grivvus.ym.music.queryDisplayNameWithoutExtension
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.resolve
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
    miniPlayer: @Composable () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backupStatus = uiState.backupStatus
    val restoreStatus = uiState.restoreStatus
    val isBackupRunning =
        backupStatus != null && !backupStatus.isFinished && !backupStatus.isFailed
    val isRestoreRunning =
        restoreStatus != null && !restoreStatus.isFinished && !restoreStatus.isFailed
    val isArchiveOperationInProgress =
        uiState.isCreatingBackup ||
            uiState.isDownloadingBackup ||
            uiState.isSavingBackup ||
            uiState.isStartingRestore
    val pendingDeleteTrackIds = uiState.pendingDeleteTrackIds
    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var uploadTrackRequest by remember { mutableStateOf<UploadTrackModalRequest?>(null) }
    var pendingRestoreRequest by remember { mutableStateOf<RestoreArchiveRequest?>(null) }
    val downloadResultSnackbarHostState = remember { SnackbarHostState() }

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
                initialTitle = context.queryDisplayNameWithoutExtension(uri),
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
                is LibraryScreenEvent.ShowDownloadResult -> {
                    downloadResultSnackbarHostState.showSnackbar(
                        DownloadResultSnackbarVisuals(
                            message = event.message.resolve(context),
                            kind = event.kind,
                        ),
                    )
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
                            stringResource(R.string.library_default_title)
                        },
                    )
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        TextButton(onClick = viewModel::clearSelection) {
                            Text(stringResource(R.string.common_action_cancel))
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        TextButton(
                            onClick = viewModel::requestDeleteSelected,
                            enabled = !uiState.isTrackMutating,
                        ) {
                            Text(stringResource(R.string.common_action_delete))
                        }
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                appIcons.Sync,
                                contentDescription = stringResource(R.string.common_cd_fetch_data_from_server),
                            )
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
                        Text(stringResource(R.string.common_action_add_track))
                    },
                )
            }
        },
        miniPlayer = miniPlayer,
        snackbarHost = {
            DownloadResultSnackbarHost(hostState = downloadResultSnackbarHostState)
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
                            isBackupRunning = isBackupRunning,
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
                    backupStatus
                        ?.takeIf { status ->
                            !status.isFinished || status.isFailed || uiState.isDownloadingBackup
                        }
                        ?.let { status ->
                            item {
                                ArchiveStatusBanner(
                                    status = status,
                                    showProgress = !status.isFailed &&
                                        (!status.isFinished || uiState.isDownloadingBackup),
                                )
                            }
                        }
                    restoreStatus
                        ?.takeIf { status -> !status.isFinished || status.isFailed }
                        ?.let { status ->
                            item {
                                ArchiveStatusBanner(status = status)
                            }
                        }
                }

                item {
                    TrackSectionHeader()
                }

                if (uiState.tracks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = stringResource(R.string.library_empty_title),
                            description = stringResource(R.string.library_empty_description),
                        )
                    }
                } else {
                    items(uiState.tracks, key = { it.id }) { track ->
                        LibraryTrackRow(
                            track = track,
                            isSelected = track.id in uiState.selectedTrackIds,
                            isSelectionMode = uiState.isSelectionMode,
                            isBusy = uiState.isTrackMutating,
                            isDownloading = track.id in uiState.downloadingTrackIds,
                            onClick = { viewModel.onTrackClick(track.id) },
                            onLongClick = { viewModel.onTrackLongPress(track.id) },
                            onDelete = { viewModel.requestDeleteTrack(track.id) },
                            onDownload = { viewModel.downloadTrack(track.id) },
                            onDeleteLocalCopy = { viewModel.deleteLocalTrackCopy(track.id) },
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
                    pluralStringResource(
                        R.plurals.library_delete_title,
                        pendingDeleteTrackIds.size,
                        pendingDeleteTrackIds.size,
                    ),
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.library_delete_message,
                        pendingDeleteTrackIds.size,
                        pendingDeleteTrackIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deletePendingTracks,
                    enabled = !uiState.isTrackMutating,
                ) {
                    Text(stringResource(R.string.common_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text(stringResource(R.string.common_action_cancel))
                }
            },
        )
    }

    if (showBackupDialog) {
        BackupOptionsDialog(
            includeImages = uiState.includeImages,
            includeTranscodedTracks = uiState.includeTranscodedTracks,
            isBusy = isArchiveOperationInProgress || isBackupRunning || isRestoreRunning,
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
            isBusy = isArchiveOperationInProgress || isBackupRunning || isRestoreRunning,
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
    isDownloading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onDownload: () -> Unit,
    onDeleteLocalCopy: () -> Unit,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (track.isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(
                                R.string.library_cd_track_available_offline,
                            ),
                            tint = OfflineAvailableColor,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.subtitle.resolve(),
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
                        contentDescription = stringResource(R.string.library_cd_track_actions),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    when {
                        isDownloading -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_action_downloading_track)) },
                                enabled = false,
                                onClick = {},
                                leadingIcon = {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                },
                            )
                        }

                        track.isDownloaded -> {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.library_action_delete_local_track_copy))
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteLocalCopy()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }

                        else -> {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_action_download_track)) },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_action_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_action_go_to_artist)) },
                        onClick = {
                            menuExpanded = false
                            onGoToArtist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_action_go_to_album)) },
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

private val OfflineAvailableColor = Color(0xFF2E7D32)
private val DownloadResultSnackbarFabClearance = 88.dp

@Composable
private fun DownloadResultSnackbarHost(
    hostState: SnackbarHostState,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = DownloadResultSnackbarFabClearance),
    ) { snackbarData ->
        val visuals = snackbarData.visuals as? DownloadResultSnackbarVisuals
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = when (visuals?.kind) {
                        LibraryDownloadResultKind.LOCAL_COPY_DELETED -> Icons.Default.Delete
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = when (visuals?.kind) {
                        LibraryDownloadResultKind.LOCAL_COPY_DELETED -> MaterialTheme.colorScheme.error
                        else -> OfflineAvailableColor
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = snackbarData.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class DownloadResultSnackbarVisuals(
    override val message: String,
    val kind: LibraryDownloadResultKind,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val duration: SnackbarDuration = SnackbarDuration.Short
    override val withDismissAction: Boolean = false
}

@Composable
private fun BackupRestoreActionsCard(
    isBusy: Boolean,
    isBackupRunning: Boolean,
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
                text = stringResource(R.string.library_backup_restore_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isBackupRunning) {
                    stringResource(R.string.library_backup_restore_status_backup_running)
                } else if (isRestoreRunning) {
                    stringResource(R.string.library_backup_restore_status_running)
                } else if (isBusy) {
                    stringResource(R.string.library_backup_restore_status_busy)
                } else {
                    stringResource(R.string.library_backup_restore_status_idle)
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
                    enabled = !isBusy && !isBackupRunning && !isRestoreRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (isBackupRunning) {
                            stringResource(R.string.library_backing_up)
                        } else {
                            stringResource(R.string.common_action_create_backup)
                        },
                    )
                }
                Button(
                    onClick = onRestoreClick,
                    enabled = !isBusy && !isBackupRunning && !isRestoreRunning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (isRestoreRunning) {
                            stringResource(R.string.library_restoring)
                        } else {
                            stringResource(R.string.common_action_restore)
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
        title = { Text(stringResource(R.string.library_backup_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.library_backup_options_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BackupOptionRow(
                    title = stringResource(R.string.library_include_images),
                    checked = includeImages,
                    onCheckedChange = onIncludeImagesChange,
                    enabled = !isBusy,
                )
                BackupOptionRow(
                    title = stringResource(R.string.library_include_transcoded_tracks),
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
                Text(stringResource(R.string.common_action_create))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
            ) {
                Text(stringResource(R.string.common_action_cancel))
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
        title = { Text(stringResource(R.string.library_restore_from_backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.library_restore_confirmation_message),
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
                Text(stringResource(R.string.common_action_restore))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBusy,
            ) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

@Composable
private fun ArchiveStatusBanner(
    status: ArchiveStatusUi,
    showProgress: Boolean = !status.isFinished && !status.isFailed,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = status.title.resolve(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = status.idLabel.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            status.sizeBytes?.let { sizeBytes ->
                Text(
                    text = stringResource(R.string.library_backup_size_bytes, sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showProgress) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = status.pollingMessage.resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    message: UiText,
    onDismiss: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message.resolve(),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.common_action_dismiss))
            }
        }
    }
}
