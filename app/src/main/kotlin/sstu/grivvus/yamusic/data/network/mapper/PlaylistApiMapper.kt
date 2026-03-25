package sstu.grivvus.yamusic.data.network.mapper

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.model.NetworkPlaylist
import sstu.grivvus.yamusic.openapi.models.PlaylistWithTracksResponse
import sstu.grivvus.yamusic.openapi.models.PlaylistsResponseInner

@Singleton
class PlaylistApiMapper @Inject constructor() {
    fun mapPlaylist(response: PlaylistWithTracksResponse): NetworkPlaylist {
        return TODO("Map playlist details response to internal playlist model")
    }

    fun mapPlaylistSummary(response: PlaylistsResponseInner): NetworkPlaylist {
        return TODO("Map playlist list item to internal playlist model")
    }

    fun mapPlaylists(response: List<PlaylistsResponseInner>): List<NetworkPlaylist> {
        return TODO("Map playlist list response to internal playlist models")
    }
}
