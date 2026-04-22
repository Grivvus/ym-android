package sstu.grivvus.ym.artist

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.ui.resolve
import sstu.grivvus.ym.ui.theme.appIcons
import androidx.compose.ui.text.input.KeyboardType

private data class CreateAlbumDraft(
    val name: String = "",
    val releaseYear: String = "",
    val coverUri: Uri? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    navigateToAlbum: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val artist = uiState.artist
    var showCreateAlbumDialog by rememberSaveable { mutableStateOf(false) }
    var createAlbumDraft by remember { mutableStateOf(CreateAlbumDraft()) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        createAlbumDraft = createAlbumDraft.copy(coverUri = uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ArtistScreenEvent.AlbumCreated -> {
                    showCreateAlbumDialog = false
                    createAlbumDraft = CreateAlbumDraft()
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
                    Text(artist?.name?.resolve() ?: stringResource(R.string.artist_default_title))
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showCreateAlbumDialog = true },
                        enabled = artist != null && !uiState.isMutating,
                    ) {
                        Text(stringResource(R.string.artist_action_add_album))
                    }
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isMutating,
                    ) {
                        Icon(
                            appIcons.Sync,
                            contentDescription = stringResource(R.string.common_cd_fetch_data_from_server),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ScreenStateHost(
            isLoading = uiState.isLoading && artist == null,
            errorMessage = uiState.errorMessage,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.padding(innerPadding),
        ) {
            if (artist != null) {
                ArtistDetails(
                    artist = artist,
                    isRefreshing = uiState.isRefreshing,
                    onAlbumClick = navigateToAlbum,
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
                        title = stringResource(R.string.artist_unavailable_title),
                        description = stringResource(R.string.artist_unavailable_description),
                    )
                }
            }
        }
    }

    if (showCreateAlbumDialog && artist != null) {
        CreateAlbumDialog(
            draft = createAlbumDraft,
            isBusy = uiState.isMutating,
            errorMessage = uiState.errorMessage?.resolve(),
            onDismiss = {
                showCreateAlbumDialog = false
                createAlbumDraft = CreateAlbumDraft()
                viewModel.dismissError()
            },
            onDismissError = viewModel::dismissError,
            onNameChange = { value ->
                createAlbumDraft = createAlbumDraft.copy(name = value)
            },
            onReleaseYearChange = { value ->
                createAlbumDraft = createAlbumDraft.copy(releaseYear = value)
            },
            onSelectCover = { coverPicker.launch("image/*") },
            onClearCover = {
                createAlbumDraft = createAlbumDraft.copy(coverUri = null)
            },
            onConfirm = {
                viewModel.createAlbum(
                    name = createAlbumDraft.name,
                    releaseYearInput = createAlbumDraft.releaseYear,
                    coverUri = createAlbumDraft.coverUri,
                )
            },
        )
    }
}

@Composable
private fun ArtistDetails(
    artist: ArtistDetailUi,
    isRefreshing: Boolean,
    onAlbumClick: (Long) -> Unit,
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
                        uri = artist.imageUri,
                        modifier = Modifier.size(120.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = artist.name.resolve(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.album_count,
                            artist.albums.size,
                            artist.albums.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isRefreshing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.artist_status_refreshing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.common_title_albums),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (artist.albums.isEmpty()) {
            item {
                EmptyStateCard(
                    title = stringResource(R.string.artist_empty_title),
                    description = stringResource(R.string.artist_empty_description),
                )
            }
        } else {
            items(artist.albums, key = { it.id }) { album ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album.id) },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(
                            uri = album.coverUri,
                            modifier = Modifier.size(72.dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = album.name.resolve(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val albumMetadataText = listOfNotNull(
                                album.releaseYear?.toString(),
                                pluralStringResource(
                                    R.plurals.track_count,
                                    album.trackCount,
                                    album.trackCount,
                                ),
                            ).joinToString(" • ")
                            Text(
                                text = albumMetadataText,
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
private fun CreateAlbumDialog(
    draft: CreateAlbumDraft,
    isBusy: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit,
    onNameChange: (String) -> Unit,
    onReleaseYearChange: (String) -> Unit,
    onSelectCover: () -> Unit,
    onClearCover: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.artist_create_album_title)) },
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
                Artwork(
                    uri = draft.coverUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onDismissError()
                            onSelectCover()
                        },
                    ) {
                        Text(stringResource(R.string.common_action_select_cover))
                    }
                    if (draft.coverUri != null) {
                        TextButton(
                            onClick = {
                                onDismissError()
                                onClearCover()
                            },
                        ) {
                            Text(stringResource(R.string.common_action_clear))
                        }
                    }
                }
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
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
