package sstu.grivvus.ym.data.network.remote.search

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.SearchApiMapper
import sstu.grivvus.ym.data.network.model.NetworkSearchType
import sstu.grivvus.ym.openapi.apis.DefaultApi
import sstu.grivvus.ym.openapi.infrastructure.ApiResponse
import sstu.grivvus.ym.openapi.infrastructure.Success
import sstu.grivvus.ym.openapi.models.SearchAlbumResult
import sstu.grivvus.ym.openapi.models.SearchArtistResult
import sstu.grivvus.ym.openapi.models.SearchResponse
import sstu.grivvus.ym.openapi.models.SearchTrackResult

@OptIn(ExperimentalCoroutinesApi::class)
class OpenApiSearchRemoteDataSourceTest {
    @Test
    fun search_usesAuthorizedGeneratedApiAndMapsResults() = runTest {
        val api = mockk<DefaultApi>()
        val querySlot = slot<String>()
        val typesSlot = slot<List<DefaultApi.TypesSearch>?>()
        val limitSlot = slot<Int?>()
        every {
            api.searchWithHttpInfo(
                capture(querySlot),
                captureNullable(typesSlot),
                captureNullable(limitSlot),
            )
        } returns Success(
            SearchResponse(
                query = "test",
                tracks = listOf(
                    SearchTrackResult(
                        trackId = 1,
                        name = "Track",
                        artistId = 2,
                        artistName = "Artist",
                        albumId = 3,
                        albumName = "Album",
                        durationMs = null,
                        isGloballyAvailable = true,
                        score = 0.95f,
                    ),
                ),
                albums = listOf(
                    SearchAlbumResult(
                        albumId = 3,
                        albumName = "Album",
                        artistId = 2,
                        artistName = "Artist",
                        releaseYear = null,
                        score = 0.75f,
                    ),
                ),
                artists = listOf(
                    SearchArtistResult(
                        artistId = 2,
                        artistName = "Artist",
                        score = 0.5f,
                    ),
                ),
            ),
            statusCode = 200,
        )
        val dataSource = OpenApiSearchRemoteDataSource(
            generatedApiProvider = object : GeneratedApiProvider {
                override suspend fun <T> withPublicApi(block: suspend (DefaultApi) -> T): T {
                    error("Search should use authorized API")
                }

                override suspend fun <T> withAuthorizedApi(block: suspend (DefaultApi) -> T): T {
                    return block(api)
                }
            },
            apiExecutor = object : ApiExecutor {
                override suspend fun <T : Any> execute(block: suspend () -> ApiResponse<T?>): T {
                    val response = block()
                    check(response is Success<*>) { "Expected Success response" }
                    @Suppress("UNCHECKED_CAST")
                    return response.data as T
                }

                override suspend fun executeUnit(block: suspend () -> ApiResponse<Unit?>) {
                    block()
                }

                override suspend fun <T> executeRaw(block: suspend () -> T): T = block()
            },
            searchApiMapper = SearchApiMapper(),
        )

        val results = dataSource.search(
            query = "test",
            types = setOf(NetworkSearchType.TRACKS, NetworkSearchType.ARTISTS),
            limit = 25,
        )

        assertThat(querySlot.captured).isEqualTo("test")
        assertThat(typesSlot.captured).containsExactly(
            DefaultApi.TypesSearch.tracks,
            DefaultApi.TypesSearch.artists,
        ).inOrder()
        assertThat(limitSlot.captured).isEqualTo(25)
        assertThat(results.query).isEqualTo("test")
        assertThat(results.tracks.single().durationMs).isNull()
        assertThat(results.tracks.single().trackId).isEqualTo(1L)
        assertThat(results.albums.single().releaseYear).isNull()
        assertThat(results.artists.single().artistId).isEqualTo(2L)
    }
}
