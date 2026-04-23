package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.network.model.NetworkAlbum
import sstu.grivvus.ym.openapi.models.AlbumInfoResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumApiMapper @Inject constructor() {
    fun mapAlbum(response: AlbumInfoResponse): NetworkAlbum {
        return NetworkAlbum(
            id = response.albumId.toLong(),
            name = response.albumName,
            releaseYear = response.releaseYear,
            releaseFullDate = response.releaseFullDate,
            trackIds = response.tracks.map { it.toLong() },
        )
    }

    fun mapAlbums(albums: List<AlbumInfoResponse>): List<NetworkAlbum> {
        return albums.map { mapAlbum(it) }
    }
}
