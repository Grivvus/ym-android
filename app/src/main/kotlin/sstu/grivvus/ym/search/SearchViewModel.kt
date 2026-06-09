package sstu.grivvus.ym.search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sstu.grivvus.ym.R
import sstu.grivvus.ym.data.MusicLibraryData
import sstu.grivvus.ym.data.MusicRepository
import sstu.grivvus.ym.data.SearchRepository
import sstu.grivvus.ym.data.SearchType
import sstu.grivvus.ym.data.network.auth.SessionExpiredException
import sstu.grivvus.ym.data.network.core.ApiException
import sstu.grivvus.ym.data.network.core.NetworkUnavailableException
import sstu.grivvus.ym.logHandledException
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.queue.PlaybackQueueFactory
import sstu.grivvus.ym.ui.UiText
import sstu.grivvus.ym.ui.asUiTextOrNull
import java.io.IOException
import javax.inject.Inject

enum class SearchFilter {
    ALL,
    TRACKS,
    ALBUMS,
    ARTISTS,
}

data class SearchUiState(
    val query: String = "",
    val selectedFilter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val isPreparingPlayback: Boolean = false,
    val results: SearchResultsUi? = null,
    val errorMessage: UiText? = null,
) {
    val hasQuery: Boolean
        get() = query.trim().isNotEmpty()
}

data class SearchResultsUi(
    val tracks: List<SearchTrackUi> = emptyList(),
    val albums: List<SearchAlbumUi> = emptyList(),
    val artists: List<SearchArtistUi> = emptyList(),
) {
    val isEmpty: Boolean
        get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty()
}

data class SearchTrackUi(
    val id: Long,
    val name: String,
    val artistId: Long,
    val artistName: String,
    val albumId: Long,
    val albumName: String,
    val durationMs: Long?,
    val artworkUri: Uri?,
)

data class SearchAlbumUi(
    val id: Long,
    val name: String,
    val artistId: Long,
    val artistName: String,
    val releaseYear: Int?,
    val coverUri: Uri?,
)

data class SearchArtistUi(
    val id: Long,
    val name: String,
    val imageUri: Uri?,
)

sealed interface SearchScreenEvent {
    data class PlayTrack(
        val queue: PlaybackQueue,
        val trackId: Long,
    ) : SearchScreenEvent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val musicRepository: MusicRepository,
    private val playbackQueueFactory: PlaybackQueueFactory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    private val _events = MutableSharedFlow<SearchScreenEvent>()
    private var searchJob: Job? = null
    private var localLibraryData: MusicLibraryData? = null

    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    val events: SharedFlow<SearchScreenEvent> = _events.asSharedFlow()

    fun updateQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                query = query,
                results = if (query.trim().isEmpty()) null else state.results,
                errorMessage = null,
            )
        }
        scheduleSearch()
    }

    fun selectFilter(filter: SearchFilter) {
        _uiState.update { state ->
            state.copy(
                selectedFilter = filter,
                errorMessage = null,
            )
        }
        scheduleSearch()
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(errorMessage = null) }
    }

    fun playTrack(trackId: Long) {
        if (_uiState.value.isPreparingPlayback) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPreparingPlayback = true,
                    errorMessage = null,
                )
            }
            try {
                val data = ensureLibraryDataWithTrack(trackId)
                val track = data.libraryTracks.firstOrNull { track ->
                    track.track.remoteId == trackId
                }
                if (track == null) {
                    _uiState.update { state ->
                        state.copy(errorMessage = UiText.StringResource(R.string.search_error_track_unavailable))
                    }
                    return@launch
                }
                val artistsById = data.artists.associateBy { artist -> artist.remoteId }
                _events.emit(
                    SearchScreenEvent.PlayTrack(
                        queue = playbackQueueFactory.singleTrackQueue(
                            track = track,
                            artistsById = artistsById,
                        ),
                        trackId = trackId,
                    ),
                )
            } catch (_: SessionExpiredException) {
                return@launch
            } catch (error: Exception) {
                error.logHandledException("SearchViewModel.playTrack")
                _uiState.update { state ->
                    state.copy(errorMessage = error.toReadableMessage())
                }
            } finally {
                _uiState.update { state -> state.copy(isPreparingPlayback = false) }
            }
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        val state = _uiState.value
        val query = state.query.trim()
        if (query.isEmpty()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    results = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(query = query, filter = _uiState.value.selectedFilter)
        }
    }

    private suspend fun runSearch(
        query: String,
        filter: SearchFilter,
    ) {
        _uiState.update { state ->
            state.copy(
                isSearching = true,
                errorMessage = null,
            )
        }
        try {
            val results = searchRepository.search(
                query = query,
                types = filter.toSearchTypes(),
            )
            val localData = loadLocalLibraryDataOrNull()
            val currentState = _uiState.value
            if (
                currentState.query.trim() != query ||
                currentState.selectedFilter != filter
            ) {
                return
            }
            _uiState.update { state ->
                state.copy(
                    isSearching = false,
                    results = results.toUi(localData),
                )
            }
        } catch (_: SessionExpiredException) {
            _uiState.update { state -> state.copy(isSearching = false) }
            return
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            error.logHandledException("SearchViewModel.runSearch")
            _uiState.update { state ->
                state.copy(
                    isSearching = false,
                    errorMessage = error.toReadableMessage(),
                )
            }
        }
    }

    private suspend fun ensureLibraryDataWithTrack(trackId: Long): MusicLibraryData {
        localLibraryData
            ?.takeIf { data ->
                data.libraryTracks.any { track -> track.track.remoteId == trackId }
            }
            ?.let { data -> return data }

        val localData = runCatching {
            musicRepository.loadLibrary(refreshFromNetwork = false)
        }.getOrNull()
        if (localData != null &&
            localData.libraryTracks.any { track -> track.track.remoteId == trackId }
        ) {
            localLibraryData = localData
            return localData
        }

        return musicRepository.loadLibrary(refreshFromNetwork = true).also { data ->
            localLibraryData = data
        }
    }

    private suspend fun loadLocalLibraryDataOrNull(): MusicLibraryData? {
        localLibraryData?.let { data -> return data }
        return runCatching {
            musicRepository.loadLibrary(refreshFromNetwork = false)
        }.getOrNull()?.also { data ->
            localLibraryData = data
        }
    }

    private fun sstu.grivvus.ym.data.SearchResults.toUi(
        localData: MusicLibraryData?,
    ): SearchResultsUi {
        val albumsById = localData?.albums.orEmpty().associateBy { album -> album.remoteId }
        val artistsById = localData?.artists.orEmpty().associateBy { artist -> artist.remoteId }
        val trackArtworkById = localData?.libraryTracks.orEmpty().associate { track ->
            val primaryAlbum = track.albums.firstOrNull()
            track.track.remoteId to primaryAlbum?.coverUri
        }
        return SearchResultsUi(
            tracks = tracks.map { track ->
                SearchTrackUi(
                    id = track.trackId,
                    name = track.name,
                    artistId = track.artistId,
                    artistName = track.artistName,
                    albumId = track.albumId,
                    albumName = track.albumName,
                    durationMs = track.durationMs,
                    artworkUri = trackArtworkById[track.trackId]
                        ?: albumsById[track.albumId]?.coverUri,
                )
            },
            albums = albums.map { album ->
                SearchAlbumUi(
                    id = album.albumId,
                    name = album.albumName,
                    artistId = album.artistId,
                    artistName = album.artistName,
                    releaseYear = album.releaseYear,
                    coverUri = albumsById[album.albumId]?.coverUri,
                )
            },
            artists = artists.map { artist ->
                SearchArtistUi(
                    id = artist.artistId,
                    name = artist.artistName,
                    imageUri = artistsById[artist.artistId]?.imageUri,
                )
            },
        )
    }

    private fun SearchFilter.toSearchTypes(): Set<SearchType>? {
        return when (this) {
            SearchFilter.ALL -> null
            SearchFilter.TRACKS -> setOf(SearchType.TRACKS)
            SearchFilter.ALBUMS -> setOf(SearchType.ALBUMS)
            SearchFilter.ARTISTS -> setOf(SearchType.ARTISTS)
        }
    }

    private fun Throwable.toReadableMessage(): UiText {
        return when (this) {
            is NetworkUnavailableException ->
                UiText.StringResource(R.string.common_error_network_request_failed)

            is ApiException -> message.asUiTextOrNull()
                ?: UiText.StringResource(R.string.common_error_server_request_failed)

            is IOException -> message.asUiTextOrNull()
                ?: UiText.StringResource(R.string.common_error_network_request_failed)

            else -> message.asUiTextOrNull()
                ?: UiText.StringResource(R.string.common_error_unexpected)
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
