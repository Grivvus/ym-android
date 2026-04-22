package sstu.grivvus.ym.playlist

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
import sstu.grivvus.ym.data.PlaylistBundle
import sstu.grivvus.ym.data.PlaylistCreationConflict
import sstu.grivvus.ym.data.TrackBundle
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.library.albumDisplayName
import sstu.grivvus.ym.library.artistDisplayName
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.queue.PlaybackQueueFactory
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiTextOrNull
import java.io.IOException
import javax.inject.Inject

data class TrackItemUi(
    val id: Long,
    val name: String,
    val subtitle: UiText,
    val coverUri: Uri? = null,
)

data class PlaylistDetailUi(
    val id: Long,
    val name: String,
    val coverUri: Uri? = null,
    val tracks: List<TrackItemUi> = emptyList(),
)

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val playlist: PlaylistDetailUi? = null,
    val libraryTracks: List<TrackItemUi> = emptyList(),
    val errorMessage: UiText? = null,
)

sealed interface PlaylistScreenEvent {
    data object NavigateBack : PlaylistScreenEvent
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playbackQueueFactory: PlaybackQueueFactory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val playlistId: Long =
        checkNotNull(savedStateHandle.get<Long>(RouteArguments.PLAYLIST_ID))
    private var currentPlaylistBundle: PlaylistBundle? = null
    private val _uiState = MutableStateFlow(PlaylistUiState())
    private val _events = MutableSharedFlow<PlaylistScreenEvent>()
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()
    val events: SharedFlow<PlaylistScreenEvent> = _events.asSharedFlow()

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
                error.logHandledException("PlaylistViewModel.refresh")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun renamePlaylist(newName: String) {
        mutate { repository.renamePlaylist(playlistId, newName) }
    }

    fun uploadPlaylistCover(coverUri: Uri) {
        mutate { repository.uploadPlaylistCover(playlistId, coverUri) }
    }

    fun addTracksToPlaylist(trackIds: Collection<Long>) {
        mutate { repository.addTracksToPlaylist(playlistId, trackIds) }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, errorMessage = null)
            try {
                repository.deletePlaylist(playlistId)
                _events.emit(PlaylistScreenEvent.NavigateBack)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (e: PlaylistCreationConflict) {
                e.logHandledException("PlaylistViewModel.deletePlaylist")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } catch (e: Exception) {
                e.logHandledException("PlaylistViewModel.deletePlaylist")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isMutating = false)
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun reloadFromLocal() {
        viewModelScope.launch {
            try {
                applyLibraryData(repository.loadLibrary(refreshFromNetwork = false))
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("PlaylistViewModel.reloadFromLocal")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            }
        }
    }

    fun playbackQueueFor(trackId: Long): PlaybackQueue? {
        val playlistBundle = currentPlaylistBundle ?: return null
        if (playlistBundle.tracks.none { it.track.remoteId == trackId }) {
            return null
        }
        return playbackQueueFactory.playlistQueue(playlistBundle, trackId)
    }

    fun playbackQueueFromStart(): PlaybackQueue? {
        val playlistBundle = currentPlaylistBundle ?: return null
        val firstTrackId = playlistBundle.tracks.firstOrNull()?.track?.remoteId ?: return null
        return playbackQueueFactory.playlistQueue(playlistBundle, firstTrackId)
    }

    private fun mutate(block: suspend () -> MusicLibraryData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, errorMessage = null)
            try {
                applyLibraryData(block())
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (e: PlaylistCreationConflict) {
                e.logHandledException("PlaylistViewModel.mutate")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } catch (e: Exception) {
                e.logHandledException("PlaylistViewModel.mutate")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isMutating = false)
            }
        }
    }

    private fun applyLibraryData(data: MusicLibraryData) {
        val artistsById = data.artists.associateBy { it.remoteId }
        val playlistBundle = data.playlists.firstOrNull { it.playlist.remoteId == playlistId }
        currentPlaylistBundle = playlistBundle
        _uiState.value = _uiState.value.copy(
            playlist = playlistBundle?.let { bundle ->
                PlaylistDetailUi(
                    id = bundle.playlist.remoteId,
                    name = bundle.playlist.name,
                    coverUri = bundle.playlist.coverUri,
                    tracks = bundle.tracks.map { track ->
                        toTrackUi(track, artistsById)
                    },
                )
            },
            libraryTracks = data.libraryTracks.map { track ->
                toTrackUi(track, artistsById)
            },
            errorMessage = if (playlistBundle == null) {
                UiText.StringResource(R.string.playlist_error_not_found)
            } else {
                null
            },
        )
    }

    private fun toTrackUi(
        track: TrackBundle,
        artistsById: Map<Long, Artist>,
    ): TrackItemUi {
        val primaryAlbum = track.albums.firstOrNull()
        val artistName = artistsById[track.track.artistId]?.let(::artistDisplayName)
        val albumName = primaryAlbum?.let(::albumDisplayName)
        return TrackItemUi(
            id = track.track.remoteId,
            name = track.track.name,
            subtitle = listOfNotNull(artistName, albumName)
                .takeIf { parts -> parts.isNotEmpty() }
                ?.let(UiText::Joined)
                ?: UiText.StringResource(R.string.common_placeholder_single),
            coverUri = primaryAlbum?.coverUri,
        )
    }

    private fun Throwable.toReadableMessage(): UiText {
        return message.asUiTextOrNull() ?: when (this) {
            is IOException -> UiText.StringResource(R.string.common_error_network_request_failed)
            else -> UiText.StringResource(R.string.common_error_unexpected)
        }
    }
}
