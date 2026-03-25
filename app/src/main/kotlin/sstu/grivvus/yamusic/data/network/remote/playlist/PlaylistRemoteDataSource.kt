package sstu.grivvus.yamusic.data.network.remote.playlist

import sstu.grivvus.yamusic.data.network.model.NetworkPlaylist
import sstu.grivvus.yamusic.data.network.model.UploadPart

interface PlaylistRemoteDataSource {
    suspend fun getMyPlaylists(): List<NetworkPlaylist>

    suspend fun getPlaylist(playlistId: Long): NetworkPlaylist

    suspend fun createPlaylist(name: String, cover: UploadPart?): Long

    suspend fun updatePlaylist(playlistId: Long, name: String): NetworkPlaylist

    suspend fun deletePlaylist(playlistId: Long)

    suspend fun addTrack(playlistId: Long, trackId: Long)

    suspend fun uploadCover(playlistId: Long, cover: UploadPart)
}
