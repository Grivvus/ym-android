package sstu.grivvus.ym.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.network.model.NetworkAlbumSearchResult
import sstu.grivvus.ym.data.network.model.NetworkArtistSearchResult
import sstu.grivvus.ym.data.network.model.NetworkSearchResults
import sstu.grivvus.ym.data.network.model.NetworkSearchType
import sstu.grivvus.ym.data.network.model.NetworkTrackSearchResult
import sstu.grivvus.ym.data.network.remote.search.SearchRemoteDataSource
import sstu.grivvus.ym.di.DefaultDispatcher
import javax.inject.Inject

class SearchRepository @Inject constructor(
    private val searchRemoteDataSource: SearchRemoteDataSource,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) {
    suspend fun search(
        query: String,
        types: Set<SearchType>? = null,
        limit: Int? = DEFAULT_SEARCH_LIMIT,
    ): SearchResults = withContext(dispatcher) {
        searchRemoteDataSource.search(
            query = query,
            types = types?.map { it.toNetworkType() }?.toSet(),
            limit = limit,
        ).toDomain()
    }

    private fun SearchType.toNetworkType(): NetworkSearchType {
        return when (this) {
            SearchType.TRACKS -> NetworkSearchType.TRACKS
            SearchType.ALBUMS -> NetworkSearchType.ALBUMS
            SearchType.ARTISTS -> NetworkSearchType.ARTISTS
        }
    }

    private fun NetworkSearchResults.toDomain(): SearchResults {
        return SearchResults(
            query = query,
            tracks = tracks.map { it.toDomain() },
            albums = albums.map { it.toDomain() },
            artists = artists.map { it.toDomain() },
        )
    }

    private fun NetworkTrackSearchResult.toDomain(): TrackSearchResult {
        return TrackSearchResult(
            trackId = trackId,
            name = name,
            artistId = artistId,
            artistName = artistName,
            albumId = albumId,
            albumName = albumName,
            durationMs = durationMs,
            isGloballyAvailable = isGloballyAvailable,
            score = score,
        )
    }

    private fun NetworkAlbumSearchResult.toDomain(): AlbumSearchResult {
        return AlbumSearchResult(
            albumId = albumId,
            albumName = albumName,
            artistId = artistId,
            artistName = artistName,
            releaseYear = releaseYear,
            score = score,
        )
    }

    private fun NetworkArtistSearchResult.toDomain(): ArtistSearchResult {
        return ArtistSearchResult(
            artistId = artistId,
            artistName = artistName,
            score = score,
        )
    }

    private companion object {
        const val DEFAULT_SEARCH_LIMIT = 10
    }
}
