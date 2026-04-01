package sstu.grivvus.ym.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sstu.grivvus.ym.playback.controller.PlaybackController
import sstu.grivvus.ym.playback.model.PlayableTrack
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.model.PlaybackUiState

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val playbackController: PlaybackController,
) : ViewModel() {
    val playbackState: StateFlow<PlaybackUiState> = playbackController.playbackState

    fun play(queue: PlaybackQueue) {
        viewModelScope.launch {
            playbackController.play(queue)
        }
    }

    fun play(track: PlayableTrack) {
        viewModelScope.launch {
            playbackController.play(track)
        }
    }

    fun togglePlayback() {
        viewModelScope.launch {
            playbackController.togglePlayback()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            playbackController.seekTo(positionMs)
        }
    }

    fun skipToNext() {
        viewModelScope.launch {
            playbackController.skipToNext()
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            playbackController.skipToPrevious()
        }
    }
}
