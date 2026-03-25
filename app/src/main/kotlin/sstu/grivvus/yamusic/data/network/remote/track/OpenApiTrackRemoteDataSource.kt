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
        return TODO("Implement track listing via generated OpenAPI client")
    }

    override suspend fun getTrack(trackId: Long): NetworkTrack {
        return TODO("Implement track details via generated OpenAPI client")
    }

    override suspend fun uploadTrack(name: String, artistId: Long, albumId: Long, track: UploadPart): Long {
        return TODO("Implement track upload via generated OpenAPI client")
    }

    override suspend fun deleteTrack(trackId: Long) {
        TODO("Implement track deletion via generated OpenAPI client")
    }
}
