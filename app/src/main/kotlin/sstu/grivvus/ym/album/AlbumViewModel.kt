package sstu.grivvus.ym.album

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.RouteArguments
import sstu.grivvus.ym.data.MusicLibraryData
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.TrackBundle
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.library.LibraryTrackItemUi
import sstu.grivvus.ym.library.albumDisplayReleaseYear
import sstu.grivvus.ym.library.albumDisplayName
import sstu.grivvus.ym.library.artistDisplayName
import sstu.grivvus.ym.library.toLibraryTrackItemUi
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.queue.PlaybackQueueFactory
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiTextOrNull
import java.io.IOException
import javax.inject.Inject

data class AlbumDetailUi(
    val id: Long,
    val name: UiText,
    val artistId: Long,
    val artistName: UiText,
    val coverUri: Uri? = null,
    val releaseYear: Int? = null,
    val tracks: List<LibraryTrackItemUi> = emptyList(),
)

data class AlbumUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val album: AlbumDetailUi? = null,
    val errorMessage: UiText? = null,
)

sealed interface AlbumScreenEvent {
    data object NavigateBack : AlbumScreenEvent
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackQueueFactory: PlaybackQueueFactory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val albumId: Long = checkNotNull(savedStateHandle.get<Long>(RouteArguments.ALBUM_ID))
    private val _uiState = MutableStateFlow(AlbumUiState())
    private val _events = MutableSharedFlow<AlbumScreenEvent>()
    private var currentAlbumTracks: List<TrackBundle> = emptyList()

    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()
    val events: SharedFlow<AlbumScreenEvent> = _events.asSharedFlow()

    init {
        refresh(initialLoad = true)
    }

    fun refresh(initialLoad: Boolean = false) {
        viewModelScope.launch {
            if (initialLoad) {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            }
            try {
                val data = repository.loadLibrary(refreshFromNetwork = true)
                applyLibraryData(data)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                val fallbackData =
                    runCatching { repository.loadLibrary(refreshFromNetwork = false) }.getOrNull()
                if (fallbackData != null) {
                    applyLibraryData(fallbackData)
                }
                error.logHandledException("AlbumViewModel.refresh")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun uploadAlbumCover(coverUri: Uri) {
        mutate { repository.uploadAlbumCover(albumId, coverUri) }
    }

    fun deleteAlbumCover() {
        mutate { repository.deleteAlbumCover(albumId) }
    }

    fun deleteAlbum() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, errorMessage = null)
            try {
                repository.deleteAlbum(albumId)
                _events.emit(AlbumScreenEvent.NavigateBack)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("AlbumViewModel.deleteAlbum")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isMutating = false)
            }
        }
    }

    fun reloadFromLocal() {
        viewModelScope.launch {
            try {
                applyLibraryData(repository.loadLibrary(refreshFromNetwork = false))
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("AlbumViewModel.reloadFromLocal")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            }
        }
    }

    fun playbackQueueFor(trackId: Long): PlaybackQueue? {
        if (currentAlbumTracks.none { bundle -> bundle.track.remoteId == trackId }) {
            return null
        }
        return playbackQueueFactory.libraryQueue(
            tracks = currentAlbumTracks,
            startTrackId = trackId,
        )
    }

    fun playbackQueueFromStart(): PlaybackQueue? {
        val firstTrackId = currentAlbumTracks.firstOrNull()?.track?.remoteId ?: return null
        return playbackQueueFactory.libraryQueue(
            tracks = currentAlbumTracks,
            startTrackId = firstTrackId,
        )
    }

    private fun applyLibraryData(data: MusicLibraryData) {
        val artistsById = data.artists.associateBy { it.remoteId }
        val album = data.albums.firstOrNull { item -> item.remoteId == albumId }
        currentAlbumTracks = data.libraryTracks.filter { track ->
            track.albums.any { linkedAlbum -> linkedAlbum.remoteId == albumId }
        }
        _uiState.value = _uiState.value.copy(
            album = album?.let { currentAlbum ->
                AlbumDetailUi(
                    id = currentAlbum.remoteId,
                    name = albumDisplayName(currentAlbum),
                    artistId = currentAlbum.artistId,
                    artistName = artistsById[currentAlbum.artistId]?.let(::artistDisplayName)
                        ?: UiText.StringResource(
                            R.string.common_placeholder_artist_id,
                            listOf(currentAlbum.artistId),
                        ),
                    coverUri = currentAlbum.coverUri,
                    releaseYear = albumDisplayReleaseYear(currentAlbum),
                    tracks = currentAlbumTracks.map { track ->
                        track.toLibraryTrackItemUi(artistsById)
                    },
                )
            },
            errorMessage = if (album == null) {
                UiText.StringResource(R.string.album_error_not_found)
            } else {
                null
            },
        )
    }

    private fun mutate(block: suspend () -> MusicLibraryData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, errorMessage = null)
            try {
                applyLibraryData(block())
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (e: Exception) {
                e.logHandledException("AlbumViewModel.mutate")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isMutating = false)
            }
        }
    }

    private fun Throwable.toReadableMessage(): UiText {
        return message.asUiTextOrNull() ?: when (this) {
            is IOException -> UiText.StringResource(R.string.common_error_network_request_failed)
            else -> UiText.StringResource(R.string.common_error_unexpected)
        }
    }
}
