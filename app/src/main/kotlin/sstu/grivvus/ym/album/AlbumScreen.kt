package sstu.grivvus.ym.album

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
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
import sstu.grivvus.ym.music.queryDisplayName
import sstu.grivvus.ym.playback.PlaybackViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    onOpenPlayer: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val album = uiState.album
    var showDeleteDialog by remember { mutableStateOf(false) }
    var uploadTrackRequest by remember { mutableStateOf<UploadTrackModalRequest?>(null) }

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
                initialTitle = context.queryDisplayName(uri),
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
            }
        }
    }

    BottomNavScaffold(
        navigateToMusic = navigateToMusic,
        navigateToLibrary = navigateToLibrary,
        navigateToProfile = navigateToProfile,
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isMutating,
                    ) {
                        Icon(
                            appIcons.Sync,
                            contentDescription = stringResource(R.string.common_cd_fetch_data_from_server),
                        )
                    }
                    if (album != null) {
                        TextButton(
                            onClick = { coverPicker.launch("image/*") },
                            enabled = !uiState.isMutating,
                        ) {
                            Text(stringResource(R.string.common_action_cover))
                        }
                        TextButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !uiState.isMutating,
                        ) {
                            Text(stringResource(R.string.common_action_delete))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        ScreenStateHost(
            isLoading = uiState.isLoading && album == null,
            errorMessage = uiState.errorMessage,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.padding(innerPadding),
        ) {
            if (album != null) {
                AlbumDetails(
                    album = album,
                    isBusy = uiState.isRefreshing || uiState.isMutating,
                    onPlayAll = {
                        viewModel.playbackQueueFromStart()?.let { queue ->
                            playbackViewModel.play(queue)
                            val trackId = queue.items.getOrNull(queue.startIndex)?.id
                                ?: return@let
                            onOpenPlayer(trackId)
                        }
                    },
                    onUploadTrack = { audioPicker.launch("audio/*") },
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
                        title = stringResource(R.string.album_unavailable_title),
                        description = stringResource(R.string.album_unavailable_description),
                    )
                }
            }
        }
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
private fun AlbumDetails(
    album: AlbumDetailUi,
    isBusy: Boolean,
    onPlayAll: () -> Unit,
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
                        uri = album.coverUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(track.id) },
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
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

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
