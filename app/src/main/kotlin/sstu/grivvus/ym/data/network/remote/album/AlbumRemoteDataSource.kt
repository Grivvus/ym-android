package sstu.grivvus.ym.data.network.remote.album

import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.AlbumApiMapper
import sstu.grivvus.ym.data.network.model.NetworkAlbum
import sstu.grivvus.ym.data.network.model.UploadPart
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface AlbumRemoteDataSource {
    suspend fun getAlbum(albumId: Long): NetworkAlbum

    suspend fun createAlbum(
        artistId: Long,
        name: String,
        cover: UploadPart? = null,
        releaseYear: Int? = null,
        releaseFullDate: LocalDate? = null,
    ): Long

    suspend fun deleteAlbum(albumId: Long)

    suspend fun uploadCover(
        albumId: Long,
        cover: UploadPart,
    )

    suspend fun deleteCover(albumId: Long)

}

@Singleton
class OpenApiAlbumRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val albumApiMapper: AlbumApiMapper,
) : AlbumRemoteDataSource {
    override suspend fun getAlbum(albumId: Long): NetworkAlbum {
        return generatedApiProvider.withAuthorizedApi { api ->
            albumApiMapper.mapAlbum(
                apiExecutor.execute {
                    api.getAlbumWithHttpInfo(albumId.toInt())
                },
            )
        }
    }

    override suspend fun createAlbum(
        artistId: Long,
        name: String,
        cover: UploadPart?,
        releaseYear: Int?,
        releaseFullDate: LocalDate?,
    ): Long {
        return generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.createAlbumWithHttpInfo(
                    artistId = artistId.toInt(),
                    albumName = name,
                    albumCover = cover?.file,
                    releaseYear = releaseYear,
                    releaseFullDate = releaseFullDate,
                )
            }.albumId.toLong()
        }
    }

    override suspend fun deleteAlbum(albumId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.deleteAlbumWithHttpInfo(albumId.toInt())
            }
        }
    }

    override suspend fun uploadCover(
        albumId: Long,
        cover: UploadPart,
    ) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.uploadAlbumCoverWithHttpInfo(
                    albumId = albumId.toInt(),
                    body = cover.file,
                )
            }
        }
    }

    override suspend fun deleteCover(albumId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.deleteAlbumCoverWithHttpInfo(albumId.toInt())
            }
        }
    }
}
