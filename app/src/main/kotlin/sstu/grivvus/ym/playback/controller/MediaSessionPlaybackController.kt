package sstu.grivvus.ym.playback.controller

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sstu.grivvus.ym.playback.model.PlayableTrack
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.model.PlaybackUiState

@Singleton
class MediaSessionPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaybackController {
    private val internalState = MutableStateFlow(PlaybackUiState())

    override val playbackState: StateFlow<PlaybackUiState> = internalState.asStateFlow()

    override suspend fun play(queue: PlaybackQueue) {
        TODO("Connect a MediaController to PlaybackService and submit a prepared queue")
    }

    override suspend fun play(track: PlayableTrack) {
        play(
            PlaybackQueue(
                source = sstu.grivvus.ym.playback.model.PlaybackSource.SingleTrack(track.id),
                items = listOf(track),
                startIndex = 0,
            ),
        )
    }

    override suspend fun pause() {
        TODO("Pause playback through MediaController")
    }

    override suspend fun resume() {
        TODO("Resume playback through MediaController")
    }

    override suspend fun togglePlayback() {
        TODO("Toggle playback through MediaController")
    }

    override suspend fun seekTo(positionMs: Long) {
        TODO("Seek current item through MediaController")
    }

    override suspend fun skipToNext() {
        TODO("Skip to next item through MediaController")
    }

    override suspend fun skipToPrevious() {
        TODO("Skip to previous item through MediaController")
    }
}
