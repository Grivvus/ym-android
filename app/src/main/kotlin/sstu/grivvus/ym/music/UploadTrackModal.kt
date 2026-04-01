package sstu.grivvus.ym.music

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.data.MusicRepository

private const val MAX_ARTIST_SUGGESTIONS = 5

@Composable
fun UploadTrackModal(
    sessionId: Long,
    playlistId: Long,
    uri: Uri,
    initialTitle: String,
    onDismiss: () -> Unit,
    onUploadSuccess: () -> Unit,
) {
    val applicationContext = LocalContext.current.applicationContext
    val repository = remember(applicationContext) {
        EntryPointAccessors.fromApplication(
            applicationContext,
            UploadTrackModalEntryPoint::class.java,
        ).musicRepository()
    }
    val viewModelStoreOwner = remember(sessionId) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(viewModelStoreOwner) {
        onDispose {
            viewModelStoreOwner.viewModelStore.clear()
        }
    }
    val modalViewModel: UploadTrackModalViewModel = viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = UploadTrackModalViewModel.factory(
            repository = repository,
            args = UploadTrackModalArgs(
                playlistId = playlistId,
                uri = uri,
                initialTitle = initialTitle,
            ),
        ),
    )
    val uiState by modalViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(modalViewModel) {
        modalViewModel.events.collectLatest { event ->
            when (event) {
                UploadTrackModalEvent.UploadCompleted -> onUploadSuccess()
            }
        }
    }

    UploadTrackModalContent(
        uiState = uiState,
        onDismiss = onDismiss,
        onTitleChange = modalViewModel::onTitleChanged,
        onArtistQueryChange = modalViewModel::onArtistQueryChanged,
        onArtistSelected = modalViewModel::onArtistSelected,
        onCreateArtist = modalViewModel::createArtistFromQuery,
        onAlbumSelected = { album ->
            modalViewModel.onAlbumSelected(album.id)
        },
        onSingleChange = modalViewModel::onSingleChanged,
        onDismissError = modalViewModel::dismissError,
        onConfirm = modalViewModel::submit,
    )
}

@Composable
private fun UploadTrackModalContent(
    uiState: UploadTrackModalUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onArtistQueryChange: (String) -> Unit,
    onArtistSelected: (UploadTrackArtistOptionUi) -> Unit,
    onCreateArtist: () -> Unit,
    onAlbumSelected: (UploadTrackAlbumOptionUi) -> Unit,
    onSingleChange: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onConfirm: () -> Unit,
) {
    var albumMenuExpanded by remember { mutableStateOf(false) }
    val normalizedArtistQuery = uiState.artistQuery.trim()
    val artistSuggestions = remember(uiState.artists, normalizedArtistQuery) {
        if (normalizedArtistQuery.isBlank()) {
            emptyList()
        } else {
            uiState.artists.filter { artist ->
                artist.displayName.startsWith(normalizedArtistQuery, ignoreCase = true)
            }.take(MAX_ARTIST_SUGGESTIONS)
        }
    }
    val hasExactArtistMatch = remember(uiState.artists, normalizedArtistQuery) {
        normalizedArtistQuery.isNotBlank() && uiState.artists.any { artist ->
            artist.displayName.equals(normalizedArtistQuery, ignoreCase = true)
        }
    }
    val selectedArtist = remember(uiState.artists, uiState.selectedArtistId) {
        uiState.artists.firstOrNull { it.id == uiState.selectedArtistId }
    }
    val artistAlbums = remember(uiState.albums, uiState.selectedArtistId) {
        uiState.albums.filter { album -> album.artistId == uiState.selectedArtistId }
    }
    val selectedAlbum = remember(artistAlbums, uiState.selectedAlbumId) {
        artistAlbums.firstOrNull { it.id == uiState.selectedAlbumId }
    }
    val hasArtistSuggestions = artistSuggestions.isNotEmpty()
    val shouldShowArtistSuggestions = normalizedArtistQuery.isNotBlank() &&
            artistSuggestions.isNotEmpty() &&
            !hasExactArtistMatch
    val shouldShowCreateArtist = normalizedArtistQuery.isNotBlank() && !hasExactArtistMatch
    val isBusy = uiState.isCatalogLoading || uiState.isCreatingArtist || uiState.isSubmitting
    val isValid = uiState.title.isNotBlank() &&
            selectedArtist != null &&
            (uiState.isSingle || selectedAlbum != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload track") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = uiState.uri.lastPathSegment.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                if (uiState.isCatalogLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading local artists and albums...",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = {
                            onDismissError()
                            onTitleChange(it)
                        },
                        label = { Text("Track title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.artistQuery,
                            onValueChange = { value ->
                                onDismissError()
                                onArtistQueryChange(value)
                            },
                            label = { Text("Artist") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (shouldShowArtistSuggestions || shouldShowCreateArtist) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 2.dp,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                            ) {
                                artistSuggestions.forEachIndexed { index, artist ->
                                    TextButton(
                                        onClick = {
                                            onDismissError()
                                            onArtistSelected(artist)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = artist.displayName,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    if (index != artistSuggestions.lastIndex || shouldShowCreateArtist) {
                                        HorizontalDivider()
                                    }
                                }
                                if (shouldShowCreateArtist) {
                                    TextButton(
                                        onClick = {
                                            onDismissError()
                                            onCreateArtist()
                                        },
                                        enabled = !uiState.isCreatingArtist,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = if (uiState.isCreatingArtist) {
                                                "Adding artist..."
                                            } else {
                                                "Add artist \"$normalizedArtistQuery\""
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    when {
                        uiState.artists.isEmpty() -> {
                            Text(
                                text = "No local artists found. Enter a name and add the artist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        uiState.artistQuery.isBlank() -> {
                            Text(
                                text = "Start typing to search artists from the local library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        selectedArtist != null -> {
                            Text(
                                text = "Selected artist: ${selectedArtist.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        shouldShowCreateArtist && hasArtistSuggestions -> {
                            Text(
                                text = "Select an artist from the suggestions or add a new one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        shouldShowCreateArtist -> {
                            Text(
                                text = "Artist not found in the local library. You can add a new one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            Text(
                                text = "Select an artist from the suggestions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = uiState.isSingle,
                            onCheckedChange = {
                                onDismissError()
                                onSingleChange(it)
                            },
                        )
                        Text(
                            text = "Single",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!uiState.isSingle) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedAlbum?.displayName.orEmpty(),
                                onValueChange = {},
                                label = { Text("Album") },
                                singleLine = true,
                                readOnly = true,
                                enabled = selectedArtist != null,
                                placeholder = {
                                    Text(
                                        if (selectedArtist == null) {
                                            "Choose artist first"
                                        } else {
                                            "Select album"
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectedArtist != null) {
                                        albumMenuExpanded = artistAlbums.isNotEmpty()
                                    },
                            )
                            DropdownMenu(
                                expanded = albumMenuExpanded && artistAlbums.isNotEmpty(),
                                onDismissRequest = { albumMenuExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                            ) {
                                artistAlbums.forEach { album ->
                                    DropdownMenuItem(
                                        text = { Text(album.displayName) },
                                        onClick = {
                                            onDismissError()
                                            onAlbumSelected(album)
                                            albumMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        when {
                            selectedArtist == null -> {
                                Text(
                                    text = "Select an artist to see available albums.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            artistAlbums.isEmpty() -> {
                                Text(
                                    text = "No albums found for this artist. Mark the track as single if needed.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                uiState.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isValid && !isBusy,
            ) {
                Text(if (uiState.isSubmitting) "Uploading..." else "Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isSubmitting) {
                Text("Cancel")
            }
        },
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UploadTrackModalEntryPoint {
    fun musicRepository(): MusicRepository
}
