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
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.PlaylistCreationConflict
import sstu.grivvus.ym.data.UploadTrackCatalog
import sstu.grivvus.ym.data.local.Album
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import java.io.IOException

data class UploadTrackModalArgs(
    val playlistId: Long,
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
    val playlistId: Long,
    val uri: Uri,
    val title: String = "",
    val artistQuery: String = "",
    val selectedArtistId: Long? = null,
    val selectedAlbumId: Long? = null,
    val isSingle: Boolean = false,
    val artists: List<UploadTrackArtistOptionUi> = emptyList(),
    val albums: List<UploadTrackAlbumOptionUi> = emptyList(),
    val isCatalogLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
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
        val exactArtist = currentState.artists.singleOrNull { artist ->
            artist.displayName.equals(value.trim(), ignoreCase = true)
        }
        _uiState.value = currentState.copy(
            artistQuery = value,
            selectedArtistId = exactArtist?.id,
            selectedAlbumId = if (exactArtist?.id == currentState.selectedArtistId) {
                currentState.selectedAlbumId
            } else {
                null
            },
            errorMessage = null,
        )
    }

    fun onArtistSelected(artist: UploadTrackArtistOptionUi) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            artistQuery = artist.displayName,
            selectedArtistId = artist.id,
            selectedAlbumId = if (artist.id == currentState.selectedArtistId) {
                currentState.selectedAlbumId
            } else {
                null
            },
            errorMessage = null,
        )
    }

    fun onAlbumSelected(albumId: Long) {
        _uiState.value = _uiState.value.copy(
            selectedAlbumId = albumId,
            errorMessage = null,
        )
    }

    fun onSingleChanged(isSingle: Boolean) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            isSingle = isSingle,
            selectedAlbumId = if (isSingle) null else currentState.selectedAlbumId,
            errorMessage = null,
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
            _uiState.value = currentState.copy(errorMessage = "Complete all required fields")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            try {
                repository.uploadTrackAndAddToPlaylist(
                    playlistId = currentState.playlistId,
                    trackUri = currentState.uri,
                    title = title,
                    artistId = artistId,
                    albumId = albumId,
                    isSingle = currentState.isSingle,
                )
                _events.emit(UploadTrackModalEvent.UploadCompleted)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
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
                    compareBy<Artist> { it.displayName().lowercase() }
                        .thenBy { it.remoteId },
                )
                .map { artist ->
                    UploadTrackArtistOptionUi(
                        id = artist.remoteId,
                        displayName = artist.displayName(),
                    )
                },
            albums = catalog.albums
                .sortedWith(
                    compareBy<Album> { it.displayName().lowercase() }
                        .thenBy { it.remoteId },
                )
                .map { album ->
                    UploadTrackAlbumOptionUi(
                        id = album.remoteId,
                        artistId = album.artistId,
                        displayName = album.displayName(),
                    )
                },
        )
    }

    private fun Artist.displayName(): String {
        return name.ifBlank { "Artist #$remoteId" }
    }

    private fun Album.displayName(): String {
        return name.ifBlank { "Album #$remoteId" }
    }

    private fun Throwable.toReadableMessage(): String {
        val messageText = message?.takeIf { it.isNotBlank() }
        return messageText ?: when (this) {
            is IOException -> "Network request failed"
            is PlaylistCreationConflict -> this.msg
            else -> "Unexpected error"
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
