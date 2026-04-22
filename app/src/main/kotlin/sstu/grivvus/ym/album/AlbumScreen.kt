package sstu.grivvus.ym.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.sharp.Sync
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.R
import sstu.grivvus.ym.components.BottomNavScaffold
import sstu.grivvus.ym.components.ScreenStateHost
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.playback.PlaybackViewModel
import sstu.grivvus.ym.ui.resolve
import sstu.grivvus.ym.ui.theme.appIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navigateToMusic: () -> Unit,
    navigateToLibrary: () -> Unit,
    navigateToProfile: () -> Unit,
    onOpenPlayer: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
    playbackViewModel: PlaybackViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val album = uiState.album
    BottomNavScaffold(
        navigateToMusic = navigateToMusic,
        navigateToLibrary = navigateToLibrary,
        navigateToProfile = navigateToProfile,
        topBar = {
            TopAppBar(
                title = { Text(album?.name?.resolve() ?: stringResource(R.string.album_default_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            appIcons.Sync,
                            contentDescription = stringResource(R.string.common_cd_fetch_data_from_server),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        ScreenStateHost(
            isLoading = uiState.isLoading && album == null,
            errorMessage = uiState.errorMessage,
            onDismissError = viewModel::dismissError,
            modifier = Modifier.padding(innerPadding),
        ) {
            if (album != null) {
                AlbumDetails(
                    album = album,
                    isBusy = uiState.isRefreshing,
                    onPlayAll = {
                        viewModel.playbackQueueFromStart()?.let { queue ->
                            playbackViewModel.play(queue)
                            val trackId = queue.items.getOrNull(queue.startIndex)?.id
                                ?: return@let
                            onOpenPlayer(trackId)
                        }
                    },
                    onTrackClick = { trackId ->
                        viewModel.playbackQueueFor(trackId)?.let { queue ->
                            playbackViewModel.play(queue)
                            onOpenPlayer(trackId)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyStateCard(
                        title = stringResource(R.string.album_unavailable_title),
                        description = stringResource(R.string.album_unavailable_description),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumDetails(
    album: AlbumDetailUi,
    isBusy: Boolean,
    onPlayAll: () -> Unit,
    onTrackClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Artwork(
                        uri = album.coverUri,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = album.name.resolve(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = album.artistName.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.track_count,
                            album.tracks.size,
                            album.tracks.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onPlayAll,
                        enabled = album.tracks.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.common_action_play_all))
                    }
                    if (isBusy) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.album_status_refreshing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.common_title_tracks),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (album.tracks.isEmpty()) {
            item {
                EmptyStateCard(
                    title = stringResource(R.string.album_empty_title),
                    description = stringResource(R.string.album_empty_description),
                )
            }
        } else {
            items(album.tracks, key = { it.id }) { track ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTrackClick(track.id) },
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    ) {
                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = track.subtitle.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
