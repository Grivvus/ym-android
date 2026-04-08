package sstu.grivvus.ym.data.network.remote.track

import okhttp3.OkHttpClient
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.mapper.TrackApiMapper
import sstu.grivvus.ym.data.network.model.NetworkTrack
import sstu.grivvus.ym.data.network.model.UploadPart
import sstu.grivvus.ym.di.TrackUploadHttpClient
import sstu.grivvus.ym.openapi.apis.DefaultApi
import javax.inject.Inject
import javax.inject.Singleton

interface TrackRemoteDataSource {
    suspend fun getMyTracks(): List<NetworkTrack>

    suspend fun getTrack(trackId: Long): NetworkTrack

    suspend fun uploadTrack(
        name: String,
        artistId: Long,
        albumId: Long?,
        isSingle: Boolean,
        track: UploadPart,
        isGloballyAvailable: Boolean? = null,
    ): Long

    suspend fun deleteTrack(trackId: Long)
}

@Singleton
class OpenApiTrackRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val trackApiMapper: TrackApiMapper,
    @param:TrackUploadHttpClient private val trackUploadHttpClient: OkHttpClient,
) : TrackRemoteDataSource {
    override suspend fun getMyTracks(): List<NetworkTrack> {
        return generatedApiProvider.withAuthorizedApi { api ->
            trackApiMapper.mapTracks(
                apiExecutor.execute {
                    api.getTracksWithHttpInfo()
                },
            )
        }
    }

    override suspend fun getTrack(trackId: Long): NetworkTrack {
        return generatedApiProvider.withAuthorizedApi { api ->
            trackApiMapper.mapTrack(
                apiExecutor.execute {
                    api.getTrackMetaWithHttpInfo(trackId.toInt())
                },
            )
        }
    }

    override suspend fun uploadTrack(
        name: String,
        artistId: Long,
        albumId: Long?,
        isSingle: Boolean,
        track: UploadPart,
        isGloballyAvailable: Boolean?,
    ): Long {
        return generatedApiProvider.withAuthorizedApi { api ->
            val uploadApi = DefaultApi(basePath = api.baseUrl, client = trackUploadHttpClient)
            apiExecutor.execute {
                uploadApi.uploadTrackWithHttpInfo(
                    name = name,
                    artistId = artistId.toInt(),
                    track = track.file,
                    albumId = albumId?.toInt(),
                    isSingle = isSingle,
                    isGloballyAvailable = null,
                )
            }.trackId.toLong()
        }
    }

    override suspend fun deleteTrack(trackId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.executeUnit {
                api.deleteTrackWithHttpInfo(trackId.toInt())
            }
        }
    }
}
