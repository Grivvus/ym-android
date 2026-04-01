package sstu.grivvus.ym.playback.model

sealed interface PlaybackSource {
    data object Library : PlaybackSource

    data class Playlist(
        val playlistId: Long,
    ) : PlaybackSource

    data class SingleTrack(
        val trackId: Long,
    ) : PlaybackSource
}

data class PlaybackQueue(
    val source: PlaybackSource,
    val items: List<PlayableTrack>,
    val startIndex: Int = 0,
    val startPositionMs: Long = 0L,
)
