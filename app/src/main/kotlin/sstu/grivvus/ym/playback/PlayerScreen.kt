package sstu.grivvus.ym.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.R
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard
import sstu.grivvus.ym.playback.model.PlayableTrack

@Composable
fun PlayerScreen(
    requestedTrackId: Long?,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrack = playbackState.currentTrack

    Surface(modifier = Modifier.fillMaxSize()) {
        if (currentTrack == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyStateCard(
                    title = stringResource(R.string.player_nothing_playing_title),
                    description = requestedTrackId?.let { trackId ->
                        stringResource(R.string.player_nothing_playing_description_requested, trackId)
                    } ?: stringResource(R.string.player_nothing_playing_description),
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.common_action_back))
                }
            }
            return@Surface
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.common_action_minimize),
                        )
                    }
                }
            }

            item {
                Artwork(
                    uri = currentTrack.artworkUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                )
            }

            item {
                PlaybackSeekBar(
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs,
                    isSeekEnabled = playbackState.isSeekable && playbackState.durationMs > 0L,
                    onSeekTo = viewModel::seekTo,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    currentTrack.artistDisplayText()?.let { artistText ->
                        Spacer(modifier = Modifier.height(8.dp))
                        PlayerMetadataLink(
                            text = artistText,
                            onClick = currentTrack.artistId?.let { artistId ->
                                { onArtistClick(artistId) }
                            },
                        )
                    }
                    currentTrack.albumDisplayText()?.let { albumText ->
                        Spacer(modifier = Modifier.height(4.dp))
                        PlayerMetadataLink(
                            text = albumText,
                            onClick = currentTrack.albumId?.let { albumId ->
                                { onAlbumClick(albumId) }
                            },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = viewModel::skipToPrevious,
                        enabled = playbackState.currentIndex > 0,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.common_cd_previous_track),
                        )
                    }
                    FilledIconButton(
                        onClick = viewModel::togglePlayback,
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = if (playbackState.isPlaying) {
                                stringResource(R.string.common_cd_pause_playback)
                            } else {
                                stringResource(R.string.common_cd_resume_playback)
                            },
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    IconButton(
                        onClick = viewModel::skipToNext,
                        enabled = playbackState.currentIndex >= 0 &&
                                playbackState.currentIndex < playbackState.queue.lastIndex,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.common_cd_next_track),
                        )
                    }
                }
            }

            if (playbackState.queue.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.player_title_queue),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                itemsIndexed(playbackState.queue, key = { _, track -> track.id }) { index, track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (index == playbackState.currentIndex) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        track.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (index < playbackState.queue.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMetadataLink(
    text: String,
    onClick: (() -> Unit)?,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (onClick == null) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.primary
        },
        fontWeight = if (onClick == null) {
            FontWeight.Normal
        } else {
            FontWeight.Medium
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(onClick = onClick)
        },
    )
}

@Composable
private fun PlayableTrack.artistDisplayText(): String? {
    return artistName?.takeIf { it.isNotBlank() }
        ?: artistId?.let { id -> stringResource(R.string.common_placeholder_artist_id, id) }
}

@Composable
private fun PlayableTrack.albumDisplayText(): String? {
    return albumName?.takeIf { it.isNotBlank() }
        ?: albumId?.let { id -> stringResource(R.string.common_placeholder_album_id, id) }
}
