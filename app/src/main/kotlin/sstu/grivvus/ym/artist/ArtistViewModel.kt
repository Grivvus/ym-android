package sstu.grivvus.ym.artist

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.RouteArguments
import sstu.grivvus.ym.data.MusicLibraryData
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.library.albumDisplayName
import sstu.grivvus.ym.library.artistDisplayName
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiTextOrNull
import java.io.IOException
import javax.inject.Inject

data class ArtistAlbumItemUi(
    val id: Long,
    val name: UiText,
    val coverUri: Uri? = null,
    val trackCount: Int,
)

data class ArtistDetailUi(
    val id: Long,
    val name: UiText,
    val imageUri: Uri? = null,
    val albums: List<ArtistAlbumItemUi> = emptyList(),
)

data class ArtistUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val artist: ArtistDetailUi? = null,
    val errorMessage: UiText? = null,
)

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: MusicRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId: Long = checkNotNull(savedStateHandle.get<Long>(RouteArguments.ARTIST_ID))
    private val _uiState = MutableStateFlow(ArtistUiState())

    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

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
                error.logHandledException("ArtistViewModel.refresh")
                _uiState.value = _uiState.value.copy(errorMessage = error.toReadableMessage())
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun applyLibraryData(data: MusicLibraryData) {
        val artist = data.artists.firstOrNull { item -> item.remoteId == artistId }
        _uiState.value = _uiState.value.copy(
            artist = artist?.let { currentArtist ->
                ArtistDetailUi(
                    id = currentArtist.remoteId,
                    name = artistDisplayName(currentArtist),
                    imageUri = currentArtist.imageUri,
                    albums = data.albums
                        .filter { album -> album.artistId == artistId }
                        .map { album ->
                            ArtistAlbumItemUi(
                                id = album.remoteId,
                                name = albumDisplayName(album),
                                coverUri = album.coverUri,
                                trackCount = data.libraryTracks.count { track ->
                                    track.albums.any { linkedAlbum -> linkedAlbum.remoteId == album.remoteId }
                                },
                            )
                        },
                )
            },
            errorMessage = if (artist == null) {
                UiText.StringResource(R.string.artist_error_not_found)
            } else {
                null
            },
        )
    }

    private fun Throwable.toReadableMessage(): UiText {
        return message.asUiTextOrNull() ?: when (this) {
            is IOException -> UiText.StringResource(R.string.common_error_network_request_failed)
            else -> UiText.StringResource(R.string.common_error_unexpected)
        }
    }
}
