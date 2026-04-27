package sstu.grivvus.ym.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sstu.grivvus.ym.R
import sstu.grivvus.ym.music.Artwork

@Composable
fun PlaybackSeekBar(
    positionMs: Long,
    durationMs: Long,
    isSeekEnabled: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
) {
    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val (isUserSeeking, setIsUserSeeking) = remember(safeDurationMs, isSeekEnabled) {
        mutableStateOf(false)
    }
    val (seekPreviewFraction, setSeekPreviewFraction) = remember(safeDurationMs, isSeekEnabled) {
        mutableFloatStateOf(0f)
    }
    val sliderFraction = if (isUserSeeking) {
        seekPreviewFraction
    } else {
        positionMs.coerceIn(0L, safeDurationMs).toFractionOf(safeDurationMs)
    }

    Column(modifier = modifier) {
        Slider(
            value = sliderFraction,
            onValueChange = { value ->
                if (isSeekEnabled) {
                    setIsUserSeeking(true)
                    setSeekPreviewFraction(value)
                }
            },
            onValueChangeFinished = {
                if (isSeekEnabled) {
                    onSeekTo((seekPreviewFraction * safeDurationMs).toLong())
                }
                setIsUserSeeking(false)
            },
            enabled = isSeekEnabled,
            valueRange = 0f..1f,
        )
        if (showTime) {
            Text(
                text = "${
                    formatDuration(
                        currentPositionMs(
                            actualPositionMs = positionMs,
                            durationMs = safeDurationMs,
                            isUserSeeking = isUserSeeking,
                            previewFraction = seekPreviewFraction,
                        ),
                    )
                } / ${formatDuration(safeDurationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MiniPlayer(
    onOpenPlayer: (Long) -> Unit,
    onDismiss: () -> Unit,
    viewModel: PlaybackViewModel,
    modifier: Modifier = Modifier,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentTrack = playbackState.currentTrack ?: return

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPlayer(currentTrack.id) }
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        uri = currentTrack.artworkUri,
                        cornerRadius = 10.dp,
                        modifier = Modifier.size(48.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        currentTrack.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_cd_close_mini_player),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = viewModel::skipToPrevious,
                    enabled = playbackState.currentIndex > 0,
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.common_cd_previous_track),
                    )
                }
                IconButton(onClick = viewModel::togglePlayback) {
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
                    )
                }
                IconButton(
                    onClick = viewModel::skipToNext,
                    enabled = playbackState.currentIndex in 0 until playbackState.queue.lastIndex,
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.common_cd_next_track),
                    )
                }
                PlaybackSeekBar(
                    positionMs = playbackState.positionMs,
                    durationMs = playbackState.durationMs,
                    isSeekEnabled = playbackState.isSeekable && playbackState.durationMs > 0L,
                    onSeekTo = viewModel::seekTo,
                    showTime = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) {
        return "00:00"
    }
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun Long.toFractionOf(totalDurationMs: Long): Float {
    if (totalDurationMs <= 0L) {
        return 0f
    }
    return (toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
}

private fun currentPositionMs(
    actualPositionMs: Long,
    durationMs: Long,
    isUserSeeking: Boolean,
    previewFraction: Float,
): Long {
    if (!isUserSeeking || durationMs <= 0L) {
        return actualPositionMs.coerceAtLeast(0L)
    }
    return (previewFraction.coerceIn(0f, 1f) * durationMs).toLong()
}
