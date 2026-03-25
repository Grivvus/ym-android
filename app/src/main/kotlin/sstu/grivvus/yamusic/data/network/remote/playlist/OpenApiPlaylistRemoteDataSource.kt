package sstu.grivvus.yamusic.data.network.remote.playlist

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.mapper.PlaylistApiMapper
import sstu.grivvus.yamusic.data.network.model.NetworkPlaylist
import sstu.grivvus.yamusic.data.network.model.UploadPart

@Singleton
class OpenApiPlaylistRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val playlistApiMapper: PlaylistApiMapper,
) : PlaylistRemoteDataSource {
    override suspend fun getMyPlaylists(): List<NetworkPlaylist> {
        return TODO("Implement playlist listing via generated OpenAPI client")
    }

    override suspend fun getPlaylist(playlistId: Long): NetworkPlaylist {
        return TODO("Implement playlist details via generated OpenAPI client")
    }

    override suspend fun createPlaylist(name: String, cover: UploadPart?): Long {
        return TODO("Implement playlist creation via generated OpenAPI client")
    }

    override suspend fun updatePlaylist(playlistId: Long, name: String): NetworkPlaylist {
        return TODO("Implement playlist update via generated OpenAPI client")
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        TODO("Implement playlist deletion via generated OpenAPI client")
    }

    override suspend fun addTrack(playlistId: Long, trackId: Long) {
        TODO("Implement adding track to playlist via generated OpenAPI client")
    }

    override suspend fun uploadCover(playlistId: Long, cover: UploadPart) {
        TODO("Implement playlist cover upload via generated OpenAPI client")
    }
}
