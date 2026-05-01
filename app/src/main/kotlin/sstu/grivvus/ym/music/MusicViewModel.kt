package sstu.grivvus.ym.music

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.MusicLibraryData
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.PlaylistFilters
import sstu.grivvus.ym.data.PlaylistType
import sstu.grivvus.ym.data.PlaylistCreationConflict
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiTextOrNull
import java.io.IOException
import javax.inject.Inject

data class PlaylistListItemUi(
    val id: Long,
    val name: String,
    val coverUri: Uri? = null,
    val trackCount: Int = 0,
    val playlistType: PlaylistType,
)

data class MusicUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val playlists: List<PlaylistListItemUi> = emptyList(),
    val playlistFilters: PlaylistFilters = PlaylistFilters(),
    val errorMessage: UiText? = null,
)

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
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
                val data = repository.loadLibrary(
                    refreshFromNetwork = true,
                    playlistFilters = _uiState.value.playlistFilters,
                )
                applyLibraryData(data)
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                val fallbackData =
                    runCatching {
                        repository.loadLibrary(
                            refreshFromNetwork = false,
                            playlistFilters = _uiState.value.playlistFilters,
                        )
                    }.getOrNull()
                if (fallbackData != null) {
                    applyLibraryData(fallbackData)
                }
                error.logHandledException("MusicViewModel.refresh")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun createPlaylist(name: String, coverUri: Uri?, isPublic: Boolean) {
        mutate { repository.createPlaylist(name, coverUri, isPublic) }
    }

    fun updatePlaylistFilters(filters: PlaylistFilters) {
        if (!filters.hasEnabledFilter || filters == _uiState.value.playlistFilters) {
            return
        }
        _uiState.value = _uiState.value.copy(playlistFilters = filters)
        refresh()
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
            } catch (e: PlaylistCreationConflict) {
                e.logHandledException("MusicViewModel.mutate")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } catch (e: Exception) {
                e.logHandledException("MusicViewModel.mutate")
                _uiState.value = _uiState.value.copy(errorMessage = e.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isMutating = false)
            }
        }
    }

    private fun applyLibraryData(data: MusicLibraryData) {
        val filters = _uiState.value.playlistFilters
        val playlists = data.playlists
            .filter { bundle -> filters.includes(bundle.playlist.playlistType) }
            .map { bundle ->
                PlaylistListItemUi(
                    id = bundle.playlist.remoteId,
                    name = bundle.playlist.name,
                    coverUri = bundle.playlist.coverUri,
                    trackCount = bundle.tracks.size,
                    playlistType = bundle.playlist.playlistType,
                )
            }
        _uiState.value = _uiState.value.copy(
            playlists = playlists,
        )
    }

    private fun Throwable.toReadableMessage(): UiText {
        return message.asUiTextOrNull() ?: when (this) {
            is IOException -> UiText.StringResource(R.string.common_error_network_request_failed)
            else -> UiText.StringResource(R.string.common_error_unexpected)
        }
    }
}
