package sstu.grivvus.ym.music

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.PlaylistCreationConflict
import sstu.grivvus.ym.data.UploadTrackCatalog
import sstu.grivvus.ym.data.local.Album
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiText
import sstu.grivvus.ym.ui.asUiTextOrNull
import java.io.IOException

data class UploadTrackModalArgs(
    val playlistId: Long?,
    val uri: Uri,
    val initialTitle: String,
)

data class UploadTrackArtistOptionUi(
    val id: Long,
    val displayName: String,
)

data class UploadTrackAlbumOptionUi(
    val id: Long,
    val artistId: Long,
    val displayName: String,
)

data class UploadTrackModalUiState(
    val playlistId: Long?,
    val uri: Uri,
    val title: String = "",
    val artistQuery: String = "",
    val albumQuery: String = "",
    val selectedArtistId: Long? = null,
    val selectedAlbumId: Long? = null,
    val isSingle: Boolean = false,
    val isGloballyAvailable: Boolean = false,
    val artists: List<UploadTrackArtistOptionUi> = emptyList(),
    val albums: List<UploadTrackAlbumOptionUi> = emptyList(),
    val isCatalogLoading: Boolean = true,
    val isAlbumsLoading: Boolean = false,
    val isCreatingArtist: Boolean = false,
    val isCreatingAlbum: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: UiText? = null,
)

sealed interface UploadTrackModalEvent {
    data object UploadCompleted : UploadTrackModalEvent
}

class UploadTrackModalViewModel(
    private val repository: MusicRepository,
    args: UploadTrackModalArgs,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        UploadTrackModalUiState(
            playlistId = args.playlistId,
            uri = args.uri,
            title = args.initialTitle,
        ),
    )
    private val _events = MutableSharedFlow<UploadTrackModalEvent>()
    val uiState: StateFlow<UploadTrackModalUiState> = _uiState.asStateFlow()
    val events: SharedFlow<UploadTrackModalEvent> = _events.asSharedFlow()

    init {
        loadCatalog()
    }

    fun onTitleChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            title = value,
            errorMessage = null,
        )
    }

    fun onArtistQueryChanged(value: String) {
        val currentState = _uiState.value
        val exactArtist = currentState.artists.firstOrNull { artist ->
            artist.displayName.equals(value.trim(), ignoreCase = true)
        }
        if (exactArtist != null && exactArtist.id != currentState.selectedArtistId) {
            onArtistSelected(exactArtist)
            return
        }
        _uiState.value = currentState.copy(
            artistQuery = value,
            selectedArtistId = exactArtist?.id,
            albumQuery = if (exactArtist?.id == currentState.selectedArtistId) {
                currentState.albumQuery
            } else {
                ""
            },
            selectedAlbumId = if (exactArtist?.id == currentState.selectedArtistId) {
                currentState.selectedAlbumId
            } else {
                null
            },
            errorMessage = null,
        )
    }

    fun createArtistFromQuery() {
        val currentState = _uiState.value
        val artistName = currentState.artistQuery.trim()
        if (artistName.isBlank()) {
            _uiState.value = currentState.copy(
                errorMessage = UiText.StringResource(R.string.common_validation_artist_name_required),
            )
            return
        }

        currentState.artists.firstOrNull { artist ->
            artist.displayName.equals(artistName, ignoreCase = true)
        }?.let { existingArtist ->
            onArtistSelected(existingArtist)
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isCreatingArtist = true, errorMessage = null)
            try {
                val createdArtist = repository.createArtist(artistName)
                val updatedArtists = (_uiState.value.artists + UploadTrackArtistOptionUi(
                    id = createdArtist.remoteId,
                    displayName = createdArtist.name,
                ))
                    .distinctBy { it.id }
                    .sortedWith(
                        compareBy<UploadTrackArtistOptionUi> { it.displayName.lowercase() }
                            .thenBy { it.id },
                    )
                _uiState.value = _uiState.value.copy(
                    artists = updatedArtists,
                    artistQuery = createdArtist.name,
                    selectedArtistId = createdArtist.remoteId,
                    albumQuery = "",
                    selectedAlbumId = null,
                    errorMessage = null,
                )
                loadAlbumsForSelectedArtist(createdArtist.remoteId)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("UploadTrackModalViewModel.createArtistFromQuery")
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.toReadableMessage(),
                )
            } finally {
                _uiState.value = _uiState.value.copy(isCreatingArtist = false)
            }
        }
    }

    fun onArtistSelected(artist: UploadTrackArtistOptionUi) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            artistQuery = artist.displayName,
            selectedArtistId = artist.id,
            albumQuery = "",
            selectedAlbumId = null,
            errorMessage = null,
        )
        loadAlbumsForSelectedArtist(artist.id)
    }

    fun onAlbumQueryChanged(value: String) {
        val currentState = _uiState.value
        val artistId = currentState.selectedArtistId
        val exactAlbum = currentState.albums.firstOrNull { album ->
            album.artistId == artistId && album.displayName.equals(value.trim(), ignoreCase = true)
        }
        _uiState.value = currentState.copy(
            albumQuery = value,
            selectedAlbumId = exactAlbum?.id,
            errorMessage = null,
        )
    }

    fun onAlbumSelected(albumId: Long) {
        val selectedAlbum = _uiState.value.albums.firstOrNull { it.id == albumId }
        _uiState.value = _uiState.value.copy(
            albumQuery = selectedAlbum?.displayName.orEmpty(),
            selectedAlbumId = albumId,
            errorMessage = null,
        )
    }

    fun createAlbumFromQuery() {
        val currentState = _uiState.value
        val artistId = currentState.selectedArtistId
        val albumName = currentState.albumQuery.trim()
        if (artistId == null) {
            _uiState.value = currentState.copy(
                errorMessage = UiText.StringResource(R.string.upload_error_select_artist_first),
            )
            return
        }
        if (albumName.isBlank()) {
            _uiState.value = currentState.copy(
                errorMessage = UiText.StringResource(R.string.common_validation_album_name_required),
            )
            return
        }

        currentState.albums.firstOrNull { album ->
            album.artistId == artistId && album.displayName.equals(albumName, ignoreCase = true)
        }?.let { existingAlbum ->
            onAlbumSelected(existingAlbum.id)
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState.copy(isCreatingAlbum = true, errorMessage = null)
            try {
                val createdAlbum = repository.createAlbum(
                    artistId = artistId,
                    name = albumName,
                )
                val updatedAlbums = mergeAlbums(
                    _uiState.value.albums,
                    listOf(
                        UploadTrackAlbumOptionUi(
                            id = createdAlbum.remoteId,
                            artistId = createdAlbum.artistId,
                            displayName = createdAlbum.name,
                        ),
                    ),
                )
                _uiState.value = _uiState.value.copy(
                    albums = updatedAlbums,
                    albumQuery = createdAlbum.name,
                    selectedAlbumId = createdAlbum.remoteId,
                    errorMessage = null,
                )
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("UploadTrackModalViewModel.createAlbumFromQuery")
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.toReadableMessage(),
                )
            } finally {
                _uiState.value = _uiState.value.copy(isCreatingAlbum = false)
            }
        }
    }

    fun onSingleChanged(isSingle: Boolean) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            isSingle = isSingle,
            albumQuery = if (isSingle) "" else currentState.albumQuery,
            selectedAlbumId = if (isSingle) null else currentState.selectedAlbumId,
            errorMessage = null,
        )
    }

    fun onAvailabilityChange(isGloballyAvailable: Boolean) {
        _uiState.value = _uiState.value.copy(
            isGloballyAvailable = isGloballyAvailable,
        )
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun submit() {
        val currentState = _uiState.value
        val title = currentState.title.trim()
        val artistId = currentState.selectedArtistId
        val albumId = if (currentState.isSingle) null else currentState.selectedAlbumId
        if (title.isBlank() || artistId == null || (!currentState.isSingle && albumId == null)) {
            _uiState.value = currentState.copy(
                errorMessage = UiText.StringResource(
                    R.string.common_validation_complete_required_fields,
                ),
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            try {
                val playlistId = currentState.playlistId
                if (playlistId != null) {
                    repository.uploadTrackAndAddToPlaylist(
                        playlistId = playlistId,
                        trackUri = currentState.uri,
                        title = title,
                        artistId = artistId,
                        albumId = albumId,
                        isSingle = currentState.isSingle,
                        isGloballyAvailable = currentState.isGloballyAvailable,
                    )
                } else {
                    repository.uploadTrackToLibrary(
                        trackUri = currentState.uri,
                        title = title,
                        artistId = artistId,
                        albumId = albumId,
                        isSingle = currentState.isSingle,
                        isGloballyAvailable = currentState.isGloballyAvailable,
                    )
                }
                _events.emit(UploadTrackModalEvent.UploadCompleted)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("UploadTrackModalViewModel.submit")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isSubmitting = false)
            }
        }
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCatalogLoading = true, errorMessage = null)
            try {
                applyCatalog(repository.loadUploadTrackCatalog(refreshFromNetwork = true))
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("UploadTrackModalViewModel.loadCatalog")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isCatalogLoading = false)
            }
        }
    }

    private fun applyCatalog(catalog: UploadTrackCatalog) {
        _uiState.value = _uiState.value.copy(
            artists = catalog.artists
                .sortedWith(
                    compareBy<Artist> { it.name.lowercase() }
                        .thenBy { it.remoteId },
                )
                .map { artist ->
                    UploadTrackArtistOptionUi(
                        id = artist.remoteId,
                        displayName = artist.name,
                    )
                },
            albums = catalog.albums
                .sortedWith(
                    compareBy<Album> { it.name.lowercase() }
                        .thenBy { it.remoteId },
                )
                .map { album ->
                    UploadTrackAlbumOptionUi(
                        id = album.remoteId,
                        artistId = album.artistId,
                        displayName = album.name,
                    )
                },
        )
    }

    private fun loadAlbumsForSelectedArtist(artistId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAlbumsLoading = true,
                errorMessage = null,
            )
            try {
                val albums = repository.loadAlbumsForArtist(artistId, refreshFromNetwork = true)
                _uiState.value = _uiState.value.copy(
                    albums = mergeAlbums(
                        _uiState.value.albums,
                        albums.map { album ->
                            UploadTrackAlbumOptionUi(
                                id = album.remoteId,
                                artistId = album.artistId,
                                displayName = album.name,
                            )
                        },
                    ),
                    albumQuery = "",
                    selectedAlbumId = null,
                )
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("UploadTrackModalViewModel.loadAlbumsForSelectedArtist")
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.toReadableMessage(),
                )
            } finally {
                _uiState.value = _uiState.value.copy(isAlbumsLoading = false)
            }
        }
    }

    private fun mergeAlbums(
        current: List<UploadTrackAlbumOptionUi>,
        incoming: List<UploadTrackAlbumOptionUi>,
    ): List<UploadTrackAlbumOptionUi> {
        return (current + incoming)
            .distinctBy { it.id }
            .sortedWith(
                compareBy<UploadTrackAlbumOptionUi> { it.displayName.lowercase() }
                    .thenBy { it.id },
            )
    }

    private fun Throwable.toReadableMessage(): UiText {
        return message.asUiTextOrNull() ?: when (this) {
            is IOException -> UiText.StringResource(R.string.common_error_network_request_failed)
            is PlaylistCreationConflict -> this.msg.asUiText()
            else -> UiText.StringResource(R.string.common_error_unexpected)
        }
    }

    companion object {
        fun factory(
            repository: MusicRepository,
            args: UploadTrackModalArgs,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(UploadTrackModalViewModel::class.java))
                    return UploadTrackModalViewModel(
                        repository = repository,
                        args = args,
                    ) as T
                }
            }
        }
    }
}
