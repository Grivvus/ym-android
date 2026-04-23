package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.network.model.NetworkPlaylist
import sstu.grivvus.ym.data.network.model.NetworkPlaylistEmpty
import sstu.grivvus.ym.openapi.models.PlaylistResponse
import sstu.grivvus.ym.openapi.models.PlaylistWithTracksResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistApiMapper @Inject constructor() {

    fun mapEmptyPlaylist(response: PlaylistResponse): NetworkPlaylistEmpty {
        return NetworkPlaylistEmpty(id = response.playlistId.toLong(), name = response.playlistName)
    }

    fun mapPlaylist(response: PlaylistWithTracksResponse): NetworkPlaylist {
        return NetworkPlaylist(
            id = response.playlistId.toLong(), name = response.playlistName,
            trackIds = response.tracks.map { it.toLong() },
        )
    }

    fun mapPlaylistSummary(response: PlaylistResponse): NetworkPlaylist {
        return NetworkPlaylist(id = response.playlistId.toLong(), name = response.playlistName)
    }

    fun mapPlaylists(response: List<PlaylistResponse>): List<NetworkPlaylist> {
        return response.map { mapPlaylistSummary(it) }
    }
}
