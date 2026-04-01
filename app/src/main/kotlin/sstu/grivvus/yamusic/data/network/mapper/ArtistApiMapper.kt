package sstu.grivvus.yamusic.data.network.mapper

import sstu.grivvus.yamusic.data.network.model.NetworkArtist
import sstu.grivvus.yamusic.openapi.models.ArtistInfoResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistApiMapper @Inject constructor() {
    fun mapArtist(artist: ArtistInfoResponse): NetworkArtist {
        return NetworkArtist(
            id = artist.artistId.toLong(),
            name = artist.artistName,
            coverUrl = artist.artistCoverUrl,
            albumIds = artist.artistAlbums.map { it.toLong() },
        )
    }

    fun mapArtists(artists: List<ArtistInfoResponse>): List<NetworkArtist> {
        return artists.map { mapArtist(it) }
    }
}
