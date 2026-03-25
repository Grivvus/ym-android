package sstu.grivvus.yamusic.music

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.sharp._360
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import sstu.grivvus.yamusic.components.BottomBar
import sstu.grivvus.yamusic.components.ErrorSnackbar
import sstu.grivvus.yamusic.ui.theme.YaMusicTheme
import sstu.grivvus.yamusic.ui.theme.appIconsMirrored

private data class CreatePlaylistDraft(
    val name: String = "",
    val coverUri: Uri? = null,
)

private data class RenamePlaylistDraft(
    val playlistId: Long,
    val value: String,
)

private data class UploadTrackDraft(
    val playlistId: Long,
    val uri: Uri,
    val title: String = "",
    val artistId: String = "",
    val albumId: String = "",
)

private sealed interface CoverPickTarget {
    data object CreatePlaylist : CoverPickTarget
    data class ExistingPlaylist(val playlistId: Long) : CoverPickTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var createDraft by remember { mutableStateOf(CreatePlaylistDraft()) }
    var renameDraft by remember { mutableStateOf<RenamePlaylistDraft?>(null) }
    var deletePlaylistId by remember { mutableStateOf<Long?>(null) }
    var addTracksPlaylistId by remember { mutableStateOf<Long?>(null) }
    var uploadTrackDraft by remember { mutableStateOf<UploadTrackDraft?>(null) }
    var coverPickTarget by remember { mutableStateOf<CoverPickTarget?>(null) }
    val selectedPlaylist = uiState.selectedPlaylist

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        when (val target = coverPickTarget) {
            CoverPickTarget.CreatePlaylist -> createDraft = createDraft.copy(coverUri = uri)
            is CoverPickTarget.ExistingPlaylist -> if (uri != null) {
                viewModel.uploadPlaylistCover(target.playlistId, uri)
            }

            null -> Unit
        }
        coverPickTarget = null
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        val playlistId = selectedPlaylist?.id ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            uploadTrackDraft = UploadTrackDraft(
                playlistId = playlistId,
                uri = uri,
                title = context.queryDisplayName(uri),
            )
        }
    }

    YaMusicTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(selectedPlaylist?.name ?: "Playlists")
                    },
                    navigationIcon = {
                        if (selectedPlaylist != null) {
                            TextButton(onClick = viewModel::closePlaylist) {
                                Text("Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(appIconsMirrored._360, contentDescription = "refresh screen")
                        }
                        if (selectedPlaylist != null) {
                            TextButton(
                                onClick = {
                                    renameDraft = RenamePlaylistDraft(
                                        playlistId = selectedPlaylist.id,
                                        value = selectedPlaylist.name,
                                    )
                                }
                            ) {
                                Text("Rename")
                            }
                            TextButton(
                                onClick = {
                                    coverPickTarget =
                                        CoverPickTarget.ExistingPlaylist(selectedPlaylist.id)
                                    coverPicker.launch("image/*")
                                }
                            ) {
                                Text("Cover")
                            }
                            TextButton(onClick = { deletePlaylistId = selectedPlaylist.id }) {
                                Text("Delete")
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (selectedPlaylist == null) {
                    FloatingActionButton(onClick = { showCreateDialog = true }) {
                        Text("+")
                    }
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
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isLoading && uiState.playlists.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    selectedPlaylist == null -> {
                        PlaylistOverview(
                            playlists = uiState.playlists,
                            isBusy = uiState.isMutating || uiState.isRefreshing,
                            onPlaylistClick = viewModel::openPlaylist,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    else -> {
                        PlaylistDetails(
                            playlist = selectedPlaylist,
                            isBusy = uiState.isMutating,
                            onAddExistingTrack = { addTracksPlaylistId = selectedPlaylist.id },
                            onUploadTrack = { audioPicker.launch("audio/*") },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                ErrorSnackbar(
                    errorMessage = uiState.errorMessage,
                    onDismiss = viewModel::dismissError,
                    modifier = Modifier.align(Alignment.BottomCenter),
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
                onSelectCover = {
                    coverPickTarget = CoverPickTarget.CreatePlaylist
                    coverPicker.launch("image/*")
                },
                onConfirm = {
                    viewModel.createPlaylist(
                        name = createDraft.name.trim(),
                        coverUri = createDraft.coverUri,
                    )
                    showCreateDialog = false
                    createDraft = CreatePlaylistDraft()
                },
            )
        }

        renameDraft?.let { draft ->
            RenamePlaylistDialog(
                draft = draft,
                isBusy = uiState.isMutating,
                onDismiss = { renameDraft = null },
                onValueChange = { value -> renameDraft = draft.copy(value = value) },
                onConfirm = {
                    viewModel.renamePlaylist(draft.playlistId, draft.value.trim())
                    renameDraft = null
                },
            )
        }

        deletePlaylistId?.let { playlistId ->
            AlertDialog(
                onDismissRequest = { deletePlaylistId = null },
                title = { Text("Delete playlist") },
                text = { Text("Playlist will be removed from the server and local storage.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deletePlaylist(playlistId)
                            deletePlaylistId = null
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletePlaylistId = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        addTracksPlaylistId?.let { playlistId ->
            val selectedIds = selectedPlaylist?.tracks?.map { it.id }?.toSet().orEmpty()
            AddTracksDialog(
                tracks = uiState.libraryTracks.filterNot { it.id in selectedIds },
                isBusy = uiState.isMutating,
                onDismiss = { addTracksPlaylistId = null },
                onConfirm = { trackIds ->
                    viewModel.addTracksToPlaylist(playlistId, trackIds)
                    addTracksPlaylistId = null
                },
            )
        }

        uploadTrackDraft?.let { draft ->
            UploadTrackDialog(
                draft = draft,
                isBusy = uiState.isMutating,
                onDismiss = { uploadTrackDraft = null },
                onTitleChange = { value -> uploadTrackDraft = draft.copy(title = value) },
                onArtistIdChange = { value -> uploadTrackDraft = draft.copy(artistId = value) },
                onAlbumIdChange = { value -> uploadTrackDraft = draft.copy(albumId = value) },
                onConfirm = {
                    val artistId = draft.artistId.toLongOrNull()
                    val albumId = draft.albumId.toLongOrNull()
                    if (artistId != null && albumId != null) {
                        viewModel.uploadTrackAndAddToPlaylist(
                            playlistId = draft.playlistId,
                            trackUri = draft.uri,
                            title = draft.title.trim(),
                            artistId = artistId,
                            albumId = albumId,
                        )
                        uploadTrackDraft = null
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaylistOverview(
    playlists: List<PlaylistListItemUi>,
    isBusy: Boolean,
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
                    text = "Your playlists",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isBusy) "Updating library..." else "Open a playlist to manage tracks and cover art.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (playlists.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No playlists yet",
                    description = "Create the first playlist from the floating action button.",
                )
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaylistClick(playlist.id) },
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
                                text = "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "Open",
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
private fun PlaylistDetails(
    playlist: PlaylistDetailUi,
    isBusy: Boolean,
    onAddExistingTrack: () -> Unit,
    onUploadTrack: () -> Unit,
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
                TrackRow(track = track)
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun TrackRow(track: TrackItemUi) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
private fun Artwork(
    uri: Uri?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (uri == null) {
            Text(
                text = "♪",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    description: String,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    draft: CreatePlaylistDraft,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onSelectCover: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onNameChange,
                    label = { Text("Playlist name") },
                    singleLine = true,
                )
                Artwork(
                    uri = draft.coverUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f),
                )
                TextButton(onClick = onSelectCover) {
                    Text("Select cover")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = draft.name.isNotBlank() && !isBusy,
            ) {
                Text("Create")
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
            OutlinedTextField(
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

@Composable
private fun UploadTrackDialog(
    draft: UploadTrackDraft,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onArtistIdChange: (String) -> Unit,
    onAlbumIdChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val isValid = draft.title.isNotBlank() &&
            draft.artistId.toLongOrNull() != null &&
            draft.albumId.toLongOrNull() != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload track") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = draft.uri.lastPathSegment.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = onTitleChange,
                    label = { Text("Track title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.artistId,
                    onValueChange = onArtistIdChange,
                    label = { Text("Artist ID") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = draft.albumId,
                    onValueChange = onAlbumIdChange,
                    label = { Text("Album ID") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = isValid && !isBusy) {
                Text("Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun Context.queryDisplayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index).orEmpty()
            }
        }
    return uri.lastPathSegment.orEmpty()
}

@Composable
fun UploadScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Upload route is no longer used", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { navController.popBackStack() }) {
            Text("Back")
        }
    }
}
