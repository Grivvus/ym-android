package sstu.grivvus.yamusic.data.network.remote.track

import sstu.grivvus.yamusic.data.network.model.NetworkTrack
import sstu.grivvus.yamusic.data.network.model.UploadPart

interface TrackRemoteDataSource {
    suspend fun getMyTracks(): List<NetworkTrack>

    suspend fun getTrack(trackId: Long): NetworkTrack

    suspend fun uploadTrack(
        name: String,
        artistId: Long,
        albumId: Long,
        track: UploadPart,
    ): Long

    suspend fun deleteTrack(trackId: Long)
}
