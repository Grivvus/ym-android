package sstu.grivvus.yamusic.data.network.remote.track

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.mapper.TrackApiMapper
import sstu.grivvus.yamusic.data.network.model.NetworkTrack
import sstu.grivvus.yamusic.data.network.model.UploadPart

@Singleton
class OpenApiTrackRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val trackApiMapper: TrackApiMapper,
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

    override suspend fun uploadTrack(name: String, artistId: Long, albumId: Long, track: UploadPart): Long {
        return generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.uploadTrackWithHttpInfo(
                    name = name,
                    artistId = artistId.toInt(),
                    track = track.file,
                    albumId = albumId.toInt(),
                    isSingle = null,
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
