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
import androidx.compose.ui.res.stringResource
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
import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.MusicRepository

private const val MAX_ARTIST_SUGGESTIONS = 5
private const val MAX_ALBUM_SUGGESTIONS = 5

@Composable
fun UploadTrackModal(
    sessionId: Long,
    playlistId: Long?,
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
        onAlbumQueryChange = modalViewModel::onAlbumQueryChanged,
        onAlbumSelected = { album ->
            modalViewModel.onAlbumSelected(album.id)
        },
        onCreateAlbum = modalViewModel::createAlbumFromQuery,
        onSingleChange = modalViewModel::onSingleChanged,
        onAvailabilityChange = modalViewModel::onAvailabilityChange,
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
    onAlbumQueryChange: (String) -> Unit,
    onAlbumSelected: (UploadTrackAlbumOptionUi) -> Unit,
    onCreateAlbum: () -> Unit,
    onSingleChange: (Boolean) -> Unit,
    onAvailabilityChange: (Boolean) -> Unit,
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
    val normalizedAlbumQuery = uiState.albumQuery.trim()
    val albumSuggestions = remember(artistAlbums, normalizedAlbumQuery) {
        if (normalizedAlbumQuery.isBlank()) {
            emptyList()
        } else {
            artistAlbums.filter { album ->
                album.displayName.startsWith(normalizedAlbumQuery, ignoreCase = true)
            }.take(MAX_ALBUM_SUGGESTIONS)
        }
    }
    val hasExactAlbumMatch = remember(artistAlbums, normalizedAlbumQuery) {
        normalizedAlbumQuery.isNotBlank() && artistAlbums.any { album ->
            album.displayName.equals(normalizedAlbumQuery, ignoreCase = true)
        }
    }
    val selectedAlbum = remember(artistAlbums, uiState.selectedAlbumId) {
        artistAlbums.firstOrNull { it.id == uiState.selectedAlbumId }
    }
    val hasArtistSuggestions = artistSuggestions.isNotEmpty()
    val shouldShowArtistSuggestions = normalizedArtistQuery.isNotBlank() &&
            artistSuggestions.isNotEmpty() &&
            !hasExactArtistMatch
    val shouldShowCreateArtist = normalizedArtistQuery.isNotBlank() && !hasExactArtistMatch
    val shouldShowAlbumSuggestions = selectedArtist != null &&
            normalizedAlbumQuery.isNotBlank() &&
            albumSuggestions.isNotEmpty() &&
            !hasExactAlbumMatch
    val shouldShowCreateAlbum = selectedArtist != null &&
            normalizedAlbumQuery.isNotBlank() &&
            !hasExactAlbumMatch
    val isBusy = uiState.isCatalogLoading ||
            uiState.isAlbumsLoading ||
            uiState.isCreatingArtist ||
            uiState.isCreatingAlbum ||
            uiState.isSubmitting
    val isValid = uiState.title.isNotBlank() &&
            selectedArtist != null &&
            (uiState.isSingle || selectedAlbum != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.upload_track_title)) },
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
                            text = stringResource(R.string.upload_loading_catalog),
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
                        label = { Text(stringResource(R.string.common_label_track_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(uiState.isGloballyAvailable, {
                            onDismissError()
                            onAvailabilityChange(it)
                        })
                        Text(
                            text = stringResource(R.string.upload_available_for_all_users),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.artistQuery,
                            onValueChange = { value ->
                                onDismissError()
                                onArtistQueryChange(value)
                            },
                            label = { Text(stringResource(R.string.common_label_artist)) },
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
                                                stringResource(R.string.upload_add_artist_loading)
                                            } else {
                                                stringResource(
                                                    R.string.upload_add_artist,
                                                    normalizedArtistQuery,
                                                )
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
                                text = stringResource(R.string.upload_no_local_artists),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        uiState.artistQuery.isBlank() -> {
                            Text(
                                text = stringResource(R.string.upload_search_artists_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        selectedArtist != null -> {
                            Text(
                                text = stringResource(
                                    R.string.upload_selected_artist,
                                    selectedArtist.displayName,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        shouldShowCreateArtist && hasArtistSuggestions -> {
                            Text(
                                text = stringResource(R.string.upload_select_or_add_artist),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        shouldShowCreateArtist -> {
                            Text(
                                text = stringResource(R.string.upload_artist_not_found),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.upload_select_artist_suggestion),
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
                            text = stringResource(R.string.upload_single),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (!uiState.isSingle) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = uiState.albumQuery,
                                onValueChange = { value ->
                                    onDismissError()
                                    onAlbumQueryChange(value)
                                },
                                label = { Text(stringResource(R.string.common_label_album)) },
                                singleLine = true,
                                enabled = selectedArtist != null,
                                placeholder = {
                                    Text(
                                        if (selectedArtist == null) {
                                            stringResource(R.string.upload_choose_artist_first)
                                        } else {
                                            stringResource(R.string.upload_select_album)
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth(),
                            )
                        }
                        if (shouldShowAlbumSuggestions || shouldShowCreateAlbum) {
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
                                    albumSuggestions.forEachIndexed { index, album ->
                                        TextButton(
                                            onClick = {
                                                onDismissError()
                                                onAlbumSelected(album)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = album.displayName,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        if (index != albumSuggestions.lastIndex || shouldShowCreateAlbum) {
                                            HorizontalDivider()
                                        }
                                    }
                                    if (shouldShowCreateAlbum) {
                                        TextButton(
                                            onClick = {
                                                onDismissError()
                                                onCreateAlbum()
                                            },
                                            enabled = !uiState.isCreatingAlbum,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                text = if (uiState.isCreatingAlbum) {
                                                    stringResource(R.string.upload_add_album_loading)
                                                } else {
                                                    stringResource(
                                                        R.string.upload_add_album,
                                                        normalizedAlbumQuery,
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        when {
                            selectedArtist == null -> {
                                Text(
                                    text = stringResource(R.string.upload_select_artist_for_albums),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            uiState.isAlbumsLoading -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator()
                                    Text(
                                        text = stringResource(
                                            R.string.upload_loading_albums_for,
                                            selectedArtist.displayName,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            selectedAlbum != null -> {
                                Text(
                                    text = stringResource(
                                        R.string.upload_selected_album,
                                        selectedAlbum.displayName,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            artistAlbums.isEmpty() -> {
                                Text(
                                    text = stringResource(R.string.upload_no_albums_for_artist),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            shouldShowCreateAlbum && albumSuggestions.isNotEmpty() -> {
                                Text(
                                    text = stringResource(R.string.upload_select_or_add_album),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            shouldShowCreateAlbum -> {
                                Text(
                                    text = stringResource(R.string.upload_album_not_found),
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
                Text(
                    stringResource(
                        if (uiState.isSubmitting) {
                            R.string.upload_uploading
                        } else {
                            R.string.common_action_upload
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !uiState.isSubmitting) {
                Text(stringResource(R.string.common_action_cancel))
            }
        },
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UploadTrackModalEntryPoint {
    fun musicRepository(): MusicRepository
}
