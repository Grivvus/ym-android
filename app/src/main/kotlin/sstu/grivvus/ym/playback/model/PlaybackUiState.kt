package sstu.grivvus.ym.playback.model

data class PlaybackUiState(
    val isConnected: Boolean = false,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTrack: PlayableTrack? = null,
    val queue: List<PlayableTrack> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
)
