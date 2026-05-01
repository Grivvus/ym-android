package sstu.grivvus.ym.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.data.PlaylistFilters
import sstu.grivvus.ym.data.PlaylistType
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
    miniPlayer: @Composable () -> Unit = {},
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

    BottomNavScaffold(
        navigateToMusic = navigateToMusic,
        navigateToLibrary = navigateToLibrary,
        navigateToProfile = navigateToProfile,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.music_title_playlists))
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            appIcons.Sync,
                            contentDescription = stringResource(R.string.common_cd_fetch_data_from_server),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Text("+")
            }
        },
        miniPlayer = miniPlayer,
    ) { innerPadding ->
        ScreenStateHost(
            isLoading = uiState.isLoading && uiState.playlists.isEmpty(),
            errorMessage = uiState.errorMessage,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.padding(innerPadding),
        ) {
            PlaylistOverview(
                playlists = uiState.playlists,
                filters = uiState.playlistFilters,
                isBusy = uiState.isMutating || uiState.isRefreshing,
                onFiltersChange = viewModel::updatePlaylistFilters,
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

@Composable
private fun PlaylistOverview(
    playlists: List<PlaylistListItemUi>,
    filters: PlaylistFilters,
    isBusy: Boolean,
    onFiltersChange: (PlaylistFilters) -> Unit,
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
                    text = stringResource(R.string.music_heading_your_playlists),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isBusy) {
                        stringResource(R.string.music_status_updating_library)
                    } else {
                        stringResource(R.string.music_description_manage_playlists)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PlaylistFilterRow(
                filters = filters,
                onFiltersChange = onFiltersChange,
            )
        }

        if (playlists.isEmpty()) {
            item {
                EmptyStateCard(
                    title = stringResource(R.string.music_empty_title),
                    description = stringResource(R.string.music_empty_description),
                )
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                val cardShape = RoundedCornerShape(12.dp)
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            BorderStroke(1.dp, playlist.playlistType.borderColor()),
                            cardShape,
                        )
                        .clickable { onPlaylistClick(playlist.id) },
                    shape = cardShape,
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
                                text = pluralStringResource(
                                    R.plurals.track_count,
                                    playlist.trackCount,
                                    playlist.trackCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = stringResource(R.string.common_action_open),
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
private fun PlaylistFilterRow(
    filters: PlaylistFilters,
    onFiltersChange: (PlaylistFilters) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            FilterChip(
                selected = filters.includeOwned,
                onClick = {
                    onFiltersChange(filters.copy(includeOwned = !filters.includeOwned))
                },
                label = { Text(stringResource(R.string.playlist_filter_owned)) },
            )
        }
        item {
            FilterChip(
                selected = filters.includeShared,
                onClick = {
                    onFiltersChange(filters.copy(includeShared = !filters.includeShared))
                },
                label = { Text(stringResource(R.string.playlist_filter_shared)) },
            )
        }
        item {
            FilterChip(
                selected = filters.includePublic,
                onClick = {
                    onFiltersChange(filters.copy(includePublic = !filters.includePublic))
                },
                label = { Text(stringResource(R.string.playlist_filter_public)) },
            )
        }
    }
}

@Composable
private fun PlaylistType.borderColor() = when (this) {
    PlaylistType.OWNED -> MaterialTheme.colorScheme.primary
    PlaylistType.SHARED -> MaterialTheme.colorScheme.secondary
    PlaylistType.PUBLIC -> MaterialTheme.colorScheme.tertiary
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
        title = { Text(stringResource(R.string.music_create_playlist_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.common_label_playlist_name)) },
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
                        text = stringResource(R.string.music_public_playlist),
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
                    Text(stringResource(R.string.common_action_select_cover))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.name.isNotBlank() && !isBusy,
            ) {
                Text(stringResource(R.string.common_action_create))
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
fun UploadScreen(
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit = {},
) {
    Scaffold(
        bottomBar = miniPlayer,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.music_upload_route_unused),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(24.dp))
            androidx.compose.material3.Button(onClick = onBack) {
                Text(stringResource(R.string.common_action_back))
            }
        }
    }
}
