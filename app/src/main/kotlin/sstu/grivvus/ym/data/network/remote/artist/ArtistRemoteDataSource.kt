package sstu.grivvus.ym.data.network.remote.artist

import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.ArtistApiMapper
import sstu.grivvus.ym.data.network.model.NetworkArtist
import sstu.grivvus.ym.data.network.model.UploadPart
import javax.inject.Inject
import javax.inject.Singleton

interface ArtistRemoteDataSource {
    suspend fun getArtist(artistId: Long): NetworkArtist

    suspend fun getAllArtists(): List<NetworkArtist>

    suspend fun createArtist(
        name: String,
        image: UploadPart? = null,
    ): Long

    suspend fun deleteArtist(artistId: Long)

    suspend fun uploadCover(
        artistId: Long,
        image: UploadPart,
    )

    suspend fun deleteCover(artistId: Long)

}

@Singleton
class OpenApiArtistRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val artistApiMapper: ArtistApiMapper,
) : ArtistRemoteDataSource {
    override suspend fun getArtist(artistId: Long): NetworkArtist {
        return generatedApiProvider.withAuthorizedApi { api ->
            artistApiMapper.mapArtist(
                apiExecutor.execute {
                    api.getArtistWithHttpInfo(artistId.toInt())
                },
            )
        }
    }

    override suspend fun getAllArtists(): List<NetworkArtist> {
        return generatedApiProvider.withAuthorizedApi { api ->
            artistApiMapper.mapArtists(
                apiExecutor.execute {
                    api.getAllArtistsWithHttpInfo(null, null)
                },
            )
        }
    }

    override suspend fun createArtist(
        name: String,
        image: UploadPart?,
    ): Long {
        return generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.createArtistWithHttpInfo(
                    artistName = name,
                    artistImage = image?.file,
                )
            }.artistId.toLong()
        }
    }

    override suspend fun deleteArtist(artistId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.deleteArtistWithHttpInfo(artistId.toInt())
            }
        }
    }

    override suspend fun uploadCover(
        artistId: Long,
        image: UploadPart,
    ) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.uploadArtistImageWithHttpInfo(
                    artistId = artistId.toInt(),
                    body = image.file,
                )
            }
        }
    }

    override suspend fun deleteCover(artistId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.deleteArtistImageWithHttpInfo(artistId.toInt())
            }
        }
    }

}
