package sstu.grivvus.ym.search

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ErrorTooltip
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.playback.PlaybackViewModel
import sstu.grivvus.ym.ui.resolve

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    navigateToAlbum: (Long) -> Unit,
    navigateToArtist: (Long) -> Unit,
    onOpenPlayer: (Long) -> Unit,
    onBack: () -> Unit,
    miniPlayer: @Composable () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SearchScreenEvent.PlayTrack -> {
                    playbackViewModel.play(event.queue)
                    onOpenPlayer(event.trackId)
                }
            }
        }
    }

    BottomNavScaffold(
        navigateToMusic = navigateToMusic,
        navigateToLibrary = navigateToLibrary,
        navigateToProfile = navigateToProfile,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_action_back))
                    }
                },
            )
        },
        miniPlayer = miniPlayer,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                SearchInput(
                    query = uiState.query,
                    onQueryChange = viewModel::updateQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                SearchFilterRow(
                    selectedFilter = uiState.selectedFilter,
                    onFilterClick = viewModel::selectFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                if (uiState.isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                SearchResultsList(
                    state = uiState,
                    onTrackClick = { track -> navigateToAlbum(track.albumId) },
                    onTrackPlay = { track -> viewModel.playTrack(track.id) },
                    onAlbumClick = { album -> navigateToAlbum(album.id) },
                    onArtistClick = { artist -> navigateToArtist(artist.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ErrorTooltip(
                message = uiState.errorMessage?.resolve().orEmpty(),
                visible = uiState.errorMessage != null,
                onDismiss = viewModel::dismissError,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SearchInput(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.search_query_label)) },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_action_clear),
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SearchFilterRow(
    selectedFilter: SearchFilter,
    onFilterClick: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SearchFilter.entries, key = { filter -> filter.name }) { filter ->
            FilterChip(
                selected = filter == selectedFilter,
                onClick = { onFilterClick(filter) },
                label = { Text(filter.label()) },
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    state: SearchUiState,
    onTrackClick: (SearchTrackUi) -> Unit,
    onTrackPlay: (SearchTrackUi) -> Unit,
    onAlbumClick: (SearchAlbumUi) -> Unit,
    onArtistClick: (SearchArtistUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = state.results
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            !state.hasQuery -> {
                item {
                    EmptyStateCard(
                        title = stringResource(R.string.search_empty_title),
                        description = stringResource(R.string.search_empty_description),
                    )
                }
            }

            results == null && state.isSearching -> Unit

            results == null || results.isEmpty -> {
                item {
                    EmptyStateCard(
                        title = stringResource(R.string.search_no_results_title),
                        description = stringResource(R.string.search_no_results_description),
                    )
                }
            }

            else -> {
                if (results.tracks.isNotEmpty()) {
                    item(key = "tracks-title") {
                        SearchSectionTitle(stringResource(R.string.common_title_tracks))
                    }
                    items(results.tracks, key = { track -> "track-${track.id}" }) { track ->
                        SearchTrackRow(
                            track = track,
                            isPreparingPlayback = state.isPreparingPlayback,
                            onClick = { onTrackClick(track) },
                            onPlay = { onTrackPlay(track) },
                        )
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item(key = "albums-title") {
                        SearchSectionTitle(stringResource(R.string.common_title_albums))
                    }
                    items(results.albums, key = { album -> "album-${album.id}" }) { album ->
                        SearchAlbumRow(
                            album = album,
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item(key = "artists-title") {
                        SearchSectionTitle(stringResource(R.string.common_title_artists))
                    }
                    items(results.artists, key = { artist -> "artist-${artist.id}" }) { artist ->
                        SearchArtistRow(
                            artist = artist,
                            onClick = { onArtistClick(artist) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionTitle(
    title: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SearchTrackRow(
    track: SearchTrackUi,
    isPreparingPlayback: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    SearchResultRow(
        title = track.name,
        subtitle = "${track.artistName} • ${track.albumName}",
        artworkUri = track.artworkUri,
        onClick = onClick,
        trailing = {
            IconButton(
                onClick = onPlay,
                enabled = !isPreparingPlayback,
            ) {
                if (isPreparingPlayback) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.search_cd_play_track),
                    )
                }
            }
        },
    )
}

@Composable
private fun SearchAlbumRow(
    album: SearchAlbumUi,
    onClick: () -> Unit,
) {
    val subtitle = album.releaseYear?.let { year ->
        "${album.artistName} • $year"
    } ?: album.artistName
    SearchResultRow(
        title = album.name,
        subtitle = subtitle,
        artworkUri = album.coverUri,
        onClick = onClick,
    )
}

@Composable
private fun SearchArtistRow(
    artist: SearchArtistUi,
    onClick: () -> Unit,
) {
    SearchResultRow(
        title = artist.name,
        subtitle = stringResource(R.string.common_label_artist),
        artworkUri = artist.imageUri,
        onClick = onClick,
    )
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    artworkUri: Uri?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Artwork(
                uri = artworkUri,
                modifier = Modifier.size(48.dp),
                cornerRadius = 12.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SearchFilter.label(): String {
    return stringResource(
        when (this) {
            SearchFilter.ALL -> R.string.search_filter_all
            SearchFilter.TRACKS -> R.string.common_title_tracks
            SearchFilter.ALBUMS -> R.string.common_title_albums
            SearchFilter.ARTISTS -> R.string.common_title_artists
        },
    )
}
