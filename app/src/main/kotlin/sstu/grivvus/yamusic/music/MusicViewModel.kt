package sstu.grivvus.yamusic.music

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.yamusic.data.MusicLibraryData
import sstu.grivvus.yamusic.data.MusicRepository
import sstu.grivvus.yamusic.data.local.LibraryTrack
import sstu.grivvus.yamusic.data.network.SessionExpiredException
import java.io.IOException
import javax.inject.Inject

data class PlaylistListItemUi(
    val id: Long,
    val name: String,
    val coverUri: Uri? = null,
    val trackCount: Int = 0,
)

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

data class MusicUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val playlists: List<PlaylistListItemUi> = emptyList(),
    val libraryTracks: List<TrackItemUi> = emptyList(),
    val selectedPlaylist: PlaylistDetailUi? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    private var latestLibraryData = MusicLibraryData(emptyList(), emptyList())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

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
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun openPlaylist(playlistId: Long) {
        val selected =
            latestLibraryData.playlists.firstOrNull { it.playlist.remoteId == playlistId } ?: return
        _uiState.value = _uiState.value.copy(
            selectedPlaylist = PlaylistDetailUi(
                id = selected.playlist.remoteId,
                name = selected.playlist.name,
                coverUri = selected.playlist.coverUri,
                tracks = selected.tracks.map(::toTrackUi),
            )
        )
    }

    fun closePlaylist() {
        _uiState.value = _uiState.value.copy(selectedPlaylist = null)
    }

    fun createPlaylist(name: String, coverUri: Uri?) {
        mutate { repository.createPlaylist(name, coverUri) }
    }

    fun deletePlaylist(playlistId: Long) {
        mutate { repository.deletePlaylist(playlistId) }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        mutate { repository.renamePlaylist(playlistId, newName) }
    }

    fun uploadPlaylistCover(playlistId: Long, coverUri: Uri) {
        mutate { repository.uploadPlaylistCover(playlistId, coverUri) }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: Collection<Long>) {
        mutate { repository.addTracksToPlaylist(playlistId, trackIds) }
    }

    fun uploadTrackAndAddToPlaylist(
        playlistId: Long,
        trackUri: Uri,
        title: String,
        artistId: Long,
        albumId: Long,
    ) {
        mutate {
            repository.uploadTrackAndAddToPlaylist(
                playlistId = playlistId,
                trackUri = trackUri,
                title = title,
                artistId = artistId,
                albumId = albumId,
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun mutate(block: suspend () -> MusicLibraryData) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMutating = true, errorMessage = null)
            try {
                applyLibraryData(block())
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isMutating = false)
            }
        }
    }

    private fun applyLibraryData(data: MusicLibraryData) {
        latestLibraryData = data
        val playlists = data.playlists.map { bundle ->
            PlaylistListItemUi(
                id = bundle.playlist.remoteId,
                name = bundle.playlist.name,
                coverUri = bundle.playlist.coverUri,
                trackCount = bundle.tracks.size,
            )
        }
        val libraryTracks = data.libraryTracks.map(::toTrackUi)
        val selectedPlaylistId = _uiState.value.selectedPlaylist?.id
        val selectedPlaylist =
            data.playlists.firstOrNull { it.playlist.remoteId == selectedPlaylistId }
        _uiState.value = _uiState.value.copy(
            playlists = playlists,
            libraryTracks = libraryTracks,
            selectedPlaylist = selectedPlaylist?.let { bundle ->
                PlaylistDetailUi(
                    id = bundle.playlist.remoteId,
                    name = bundle.playlist.name,
                    coverUri = bundle.playlist.coverUri,
                    tracks = bundle.tracks.map(::toTrackUi),
                )
            },
        )
    }

    private fun toTrackUi(track: LibraryTrack): TrackItemUi {
        return TrackItemUi(
            id = track.remoteId,
            name = track.name,
            subtitle = "Artist #${track.artistId}",
            coverUri = track.coverUri,
        )
    }

    private fun Throwable.toReadableMessage(): String {
        val messageText = message?.takeIf { it.isNotBlank() }
        return messageText ?: when (this) {
            is IOException -> "Network request failed"
            else -> "Unexpected error"
        }
    }
}
