package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.network.model.NetworkAlbumSearchResult
import sstu.grivvus.ym.data.network.model.NetworkArtistSearchResult
import sstu.grivvus.ym.data.network.model.NetworkSearchResults
import sstu.grivvus.ym.data.network.model.NetworkTrackSearchResult
import sstu.grivvus.ym.openapi.models.SearchAlbumResult
import sstu.grivvus.ym.openapi.models.SearchArtistResult
import sstu.grivvus.ym.openapi.models.SearchResponse
import sstu.grivvus.ym.openapi.models.SearchTrackResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchApiMapper @Inject constructor() {
    fun mapSearchResponse(response: SearchResponse): NetworkSearchResults {
        return NetworkSearchResults(
            query = response.query,
            tracks = response.tracks.map { it.toNetwork() },
            albums = response.albums.map { it.toNetwork() },
            artists = response.artists.map { it.toNetwork() },
        )
    }

    private fun SearchTrackResult.toNetwork(): NetworkTrackSearchResult {
        return NetworkTrackSearchResult(
            trackId = trackId.toLong(),
            name = name,
            artistId = artistId.toLong(),
            artistName = artistName,
            albumId = albumId.toLong(),
            albumName = albumName,
            durationMs = durationMs?.toLong(),
            isGloballyAvailable = isGloballyAvailable,
            score = score,
        )
    }

    private fun SearchAlbumResult.toNetwork(): NetworkAlbumSearchResult {
        return NetworkAlbumSearchResult(
            albumId = albumId.toLong(),
            albumName = albumName,
            artistId = artistId.toLong(),
            artistName = artistName,
            releaseYear = releaseYear,
            score = score,
        )
    }

    private fun SearchArtistResult.toNetwork(): NetworkArtistSearchResult {
        return NetworkArtistSearchResult(
            artistId = artistId.toLong(),
            artistName = artistName,
            score = score,
        )
    }
}
