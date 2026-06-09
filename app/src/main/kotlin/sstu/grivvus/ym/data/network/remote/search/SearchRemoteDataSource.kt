package sstu.grivvus.ym.data.network.remote.search

import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.SearchApiMapper
import sstu.grivvus.ym.data.network.model.NetworkSearchResults
import sstu.grivvus.ym.data.network.model.NetworkSearchType
import sstu.grivvus.ym.openapi.apis.DefaultApi
import javax.inject.Inject
import javax.inject.Singleton

interface SearchRemoteDataSource {
    suspend fun search(
        query: String,
        types: Set<NetworkSearchType>? = null,
        limit: Int? = null,
    ): NetworkSearchResults
}

@Singleton
class OpenApiSearchRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val searchApiMapper: SearchApiMapper,
) : SearchRemoteDataSource {
    override suspend fun search(
        query: String,
        types: Set<NetworkSearchType>?,
        limit: Int?,
    ): NetworkSearchResults {
        return generatedApiProvider.withAuthorizedApi { api ->
            searchApiMapper.mapSearchResponse(
                apiExecutor.execute {
                    api.searchWithHttpInfo(
                        q = query,
                        types = types?.map { it.toGeneratedType() },
                        limit = limit,
                    )
                },
            )
        }
    }

    private fun NetworkSearchType.toGeneratedType(): DefaultApi.TypesSearch {
        return when (this) {
            NetworkSearchType.TRACKS -> DefaultApi.TypesSearch.tracks
            NetworkSearchType.ALBUMS -> DefaultApi.TypesSearch.albums
            NetworkSearchType.ARTISTS -> DefaultApi.TypesSearch.artists
        }
    }
}
