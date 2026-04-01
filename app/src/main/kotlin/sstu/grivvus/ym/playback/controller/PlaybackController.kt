package sstu.grivvus.ym.playback.controller

import kotlinx.coroutines.flow.StateFlow
import sstu.grivvus.ym.playback.model.PlayableTrack
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.model.PlaybackUiState

interface PlaybackController {
    val playbackState: StateFlow<PlaybackUiState>

    suspend fun play(queue: PlaybackQueue)

    suspend fun play(track: PlayableTrack)

    suspend fun pause()

    suspend fun resume()

    suspend fun togglePlayback()

    suspend fun seekTo(positionMs: Long)

    suspend fun skipToNext()

    suspend fun skipToPrevious()
}
