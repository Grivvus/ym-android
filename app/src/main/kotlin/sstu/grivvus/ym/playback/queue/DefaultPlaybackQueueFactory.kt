package sstu.grivvus.ym.playback.queue

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.ym.data.PlaylistBundle
import sstu.grivvus.ym.data.TrackBundle
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.playback.model.PlayableTrack
import sstu.grivvus.ym.playback.model.PlaybackQueue
import sstu.grivvus.ym.playback.model.PlaybackSource

@Singleton
class DefaultPlaybackQueueFactory @Inject constructor() : PlaybackQueueFactory {
    override fun libraryQueue(
        tracks: List<TrackBundle>,
        startTrackId: Long,
    ): PlaybackQueue {
        val playableTracks = tracks.map(::toPlayableTrack)
        return PlaybackQueue(
            source = PlaybackSource.Library,
            items = playableTracks,
            startIndex = playableTracks.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0),
        )
    }

    override fun playlistQueue(
        playlist: PlaylistBundle,
        startTrackId: Long,
    ): PlaybackQueue {
        val playableTracks = playlist.tracks.map(::toPlayableTrack)
        return PlaybackQueue(
            source = PlaybackSource.Playlist(playlist.playlist.remoteId),
            items = playableTracks,
            startIndex = playableTracks.indexOfFirst { it.id == startTrackId }.coerceAtLeast(0),
        )
    }

    private fun toPlayableTrack(track: TrackBundle): PlayableTrack {
        val primaryAlbum = track.albums.firstOrNull()
        val qualityUris = buildMap {
            track.track.uriFast?.let { put(TrackQuality.FAST, it) }
            track.track.uriStandard?.let { put(TrackQuality.STANDARD, it) }
            track.track.uriHigh?.let { put(TrackQuality.HIGH, it) }
            track.track.uriLossless?.let { put(TrackQuality.LOSSLESS, it) }
        }
        return PlayableTrack(
            id = track.track.remoteId,
            title = track.track.name,
            subtitle = primaryAlbum?.name?.takeIf { it.isNotBlank() },
            artworkUri = primaryAlbum?.coverUri,
            durationMs = track.track.durationMs,
            localPath = track.track.localPath,
            qualityUris = qualityUris,
        )
    }
}
