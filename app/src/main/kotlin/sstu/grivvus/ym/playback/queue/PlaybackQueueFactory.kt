package sstu.grivvus.ym.playback.queue

import sstu.grivvus.ym.data.PlaylistBundle
import sstu.grivvus.ym.data.TrackBundle
import sstu.grivvus.ym.playback.model.PlaybackQueue

interface PlaybackQueueFactory {
    fun libraryQueue(
        tracks: List<TrackBundle>,
        startTrackId: Long,
    ): PlaybackQueue

    fun playlistQueue(
        playlist: PlaylistBundle,
        startTrackId: Long,
    ): PlaybackQueue
}
