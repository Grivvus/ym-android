package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.PlaylistType
import sstu.grivvus.ym.data.network.model.NetworkPlaylist
import sstu.grivvus.ym.data.network.model.NetworkPlaylistDetails
import sstu.grivvus.ym.data.network.model.NetworkPlaylistEmpty
import sstu.grivvus.ym.openapi.models.ExtendedPlaylist
import sstu.grivvus.ym.openapi.models.PlaylistResponse
import sstu.grivvus.ym.openapi.models.PlaylistWithTracksResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistApiMapper @Inject constructor() {

    fun mapEmptyPlaylist(response: PlaylistResponse): NetworkPlaylistEmpty {
        return NetworkPlaylistEmpty(id = response.playlistId.toLong(), name = response.playlistName)
    }

    fun mapPlaylist(response: PlaylistWithTracksResponse): NetworkPlaylistDetails {
        return NetworkPlaylistDetails(
            id = response.playlistId.toLong(), name = response.playlistName,
            trackIds = response.tracks.map { it.toLong() },
        )
    }

    fun mapPlaylistSummary(response: ExtendedPlaylist): NetworkPlaylist {
        val playlistType = response.playlistType.toPlaylistType()
        return NetworkPlaylist(
            id = response.playlistId.toLong(),
            name = response.playlistName,
            ownerRemoteId = response.playlistOwnerId.toLong(),
            playlistType = playlistType,
            canEdit = playlistType.canEditByDefault(),
        )
    }

    fun mapPlaylists(response: List<ExtendedPlaylist>): List<NetworkPlaylist> {
        return response.map { mapPlaylistSummary(it) }
    }

    private fun ExtendedPlaylist.PlaylistType.toPlaylistType(): PlaylistType {
        return when (this) {
            ExtendedPlaylist.PlaylistType.owned -> PlaylistType.OWNED
            ExtendedPlaylist.PlaylistType.`public` -> PlaylistType.PUBLIC
            ExtendedPlaylist.PlaylistType.shared -> PlaylistType.SHARED
        }
    }

    private fun PlaylistType.canEditByDefault(): Boolean {
        return when (this) {
            PlaylistType.OWNED,
            PlaylistType.SHARED -> true
            PlaylistType.PUBLIC -> false
        }
    }
}
