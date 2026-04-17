package sstu.grivvus.ym.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.components.BottomBar
import sstu.grivvus.ym.components.ErrorSnackbar
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.ui.theme.YMTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restoreStatus = uiState.restoreStatus
    val isRestoreRunning = restoreStatus != null && !restoreStatus.isFinished && !restoreStatus.isFailed
    val isArchiveOperationInProgress =
        uiState.isCreatingBackup || uiState.isSavingBackup || uiState.isStartingRestore

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

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LibraryScreenEvent.RequestBackupSaveLocation -> {
                    backupSaveLauncher.launch(event.suggestedFileName)
                }
            }
        }
    }

    YMTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Library") },
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
