package sstu.grivvus.ym.music

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.ym.logHandledUiError
import sstu.grivvus.ym.RouteArguments
import sstu.grivvus.ym.data.MusicLibraryData
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.PlaylistBundle
import sstu.grivvus.ym.data.PlaylistCreationConflict
import sstu.grivvus.ym.data.TrackBundle
import sstu.grivvus.ym.data.local.Album
import sstu.grivvus.ym.data.local.Artist
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.queue.PlaybackQueueFactory
import java.io.IOException

data class TrackItemUi(
    val id: Long,
    val name: String,
    val subtitle: String,
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
    val errorMessage: String? = null,
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
                error.logHandledUiError("PlaylistViewModel.refresh")
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
                e.logHandledUiError("PlaylistViewModel.deletePlaylist")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } catch (e: Exception) {
                e.logHandledUiError("PlaylistViewModel.deletePlaylist")
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
                error.logHandledUiError("PlaylistViewModel.reloadFromLocal")
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
                e.logHandledUiError("PlaylistViewModel.mutate")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } catch (e: Exception) {
                e.logHandledUiError("PlaylistViewModel.mutate")
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
                "Playlist was not found"
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
            subtitle = listOfNotNull(
                artistName?.takeIf { it.isNotBlank() },
                albumName?.takeIf { it.isNotBlank() },
            ).joinToString(" • ").ifBlank { "Single" },
            coverUri = primaryAlbum?.coverUri,
        )
    }

    private fun artistDisplayName(artist: Artist): String {
        return artist.name.ifBlank { "Artist #${artist.remoteId}" }
    }

    private fun albumDisplayName(album: Album): String {
        return album.name.ifBlank { "Album #${album.remoteId}" }
    }

    private fun Throwable.toReadableMessage(): String {
        val messageText = message?.takeIf { it.isNotBlank() }
        return messageText ?: when (this) {
            is IOException -> "Network request failed"
            else -> "Unexpected error"
        }
    }
}
