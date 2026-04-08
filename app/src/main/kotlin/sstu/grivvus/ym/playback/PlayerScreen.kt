package sstu.grivvus.ym.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.music.Artwork
import sstu.grivvus.ym.music.EmptyStateCard

@Composable
fun PlayerScreen(
    requestedTrackId: Long?,
    onBack: () -> Unit,
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
                    title = "Nothing is playing",
                    description = requestedTrackId?.let { trackId ->
                        "Open a track from a playlist to start playback. Requested track id: $trackId."
                    } ?: "Open a track from a playlist to start playback.",
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onBack) {
                    Text("Back")
                }
            }
            return@Surface
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TextButton(onClick = onBack) {
                    Text("Minimize")
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
                    )
                    currentTrack.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = viewModel::skipToPrevious,
                        enabled = playbackState.currentIndex > 0,
                    ) {
                        Text("Previous")
                    }
                    Button(onClick = viewModel::togglePlayback) {
                        Text(if (playbackState.isPlaying) "Pause" else "Play")
                    }
                    Button(
                        onClick = viewModel::skipToNext,
                        enabled = playbackState.currentIndex >= 0 &&
                                playbackState.currentIndex < playbackState.queue.lastIndex,
                    ) {
                        Text("Next")
                    }
                }
            }

            if (playbackState.queue.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Queue",
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
