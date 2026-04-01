package sstu.grivvus.yamusic.data.network.remote.playlist

import sstu.grivvus.yamusic.data.network.auth.AuthSessionManager
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.mapper.PlaylistApiMapper
import sstu.grivvus.yamusic.data.network.model.NetworkPlaylist
import sstu.grivvus.yamusic.data.network.model.UploadPart
import sstu.grivvus.yamusic.openapi.models.AddTrackToPlaylistRequest
import sstu.grivvus.yamusic.openapi.models.PlaylistResponse
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistRemoteDataSource {
    suspend fun getMyPlaylists(): List<NetworkPlaylist>

    suspend fun getPlaylist(playlistId: Long): NetworkPlaylist

    suspend fun createPlaylist(name: String, cover: UploadPart?): Long

    suspend fun updatePlaylist(playlistId: Long, name: String): NetworkPlaylist

    suspend fun deletePlaylist(playlistId: Long)

    suspend fun addTrack(playlistId: Long, trackId: Long)

    suspend fun uploadCover(playlistId: Long, cover: UploadPart)

    suspend fun deleteCover(playlistId: Long)
}

@Singleton
class OpenApiPlaylistRemoteDataSource @Inject constructor(
    private val authSessionManager: AuthSessionManager,
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val playlistApiMapper: PlaylistApiMapper,
) : PlaylistRemoteDataSource {
    override suspend fun getMyPlaylists(): List<NetworkPlaylist> {
        return generatedApiProvider.withAuthorizedApi { api ->
            playlistApiMapper.mapPlaylists(
                apiExecutor.execute {
                    api.getPlaylistsWithHttpInfo()
                },
            )
        }
    }

    override suspend fun getPlaylist(playlistId: Long): NetworkPlaylist {
        return generatedApiProvider.withAuthorizedApi { api ->
            playlistApiMapper.mapPlaylist(
                apiExecutor.execute {
                    api.getPlaylistWithHttpInfo(playlistId.toInt())
                },
            )
        }
    }

    override suspend fun createPlaylist(name: String, cover: UploadPart?): Long {
        val currentUser = authSessionManager.requireCurrentUser()
        return generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.createPlaylistWithHttpInfo(
                    ownerId = currentUser.remoteId.toInt(),
                    playlistName = name,
                    playlistCover = cover?.file,
                )
            }.playlistId.toLong()
        }
    }

    override suspend fun updatePlaylist(playlistId: Long, name: String): NetworkPlaylist {
        return generatedApiProvider.withAuthorizedApi { api ->
            playlistApiMapper.mapPlaylist(
                apiExecutor.execute {
                    api.updatePlaylistWithHttpInfo(
                        playlistId = playlistId.toInt(),
                        playlistResponse = PlaylistResponse(
                            playlistId = playlistId.toInt(),
                            playlistName = name,
                        ),
                    )
                },
            )
        }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.deletePlaylistWithHttpInfo(playlistId.toInt())
            }
        }
    }

    override suspend fun addTrack(playlistId: Long, trackId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.executeUnit {
                api.addTrackToPlaylistWithHttpInfo(
                    playlistId = playlistId.toInt(),
                    addTrackToPlaylistRequest = AddTrackToPlaylistRequest(trackId = trackId.toInt()),
                )
            }
        }
    }

    override suspend fun uploadCover(playlistId: Long, cover: UploadPart) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.uploadPlaylistCoverWithHttpInfo(
                    playlistId = playlistId.toInt(),
                    body = cover.file,
                )
            }
        }
    }

    override suspend fun deleteCover(playlistId: Long) {
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.deletePlaylistCoverWithHttpInfo(playlistId.toInt())
            }
        }
    }
}
