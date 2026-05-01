package sstu.grivvus.ym.data.network.remote.playlist

import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.PlaylistFilters
import sstu.grivvus.ym.data.network.mapper.PlaylistApiMapper
import sstu.grivvus.ym.data.network.model.NetworkPlaylist
import sstu.grivvus.ym.data.network.model.NetworkPlaylistDetails
import sstu.grivvus.ym.data.network.model.NetworkPlaylistEmpty
import sstu.grivvus.ym.data.network.model.UploadPart
import sstu.grivvus.ym.openapi.models.AddTrackToPlaylistRequest
import sstu.grivvus.ym.openapi.models.PlaylistUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistRemoteDataSource {
    suspend fun getAvailablePlaylists(filters: PlaylistFilters = PlaylistFilters()): List<NetworkPlaylist>

    suspend fun getPlaylist(playlistId: Long): NetworkPlaylistDetails

    suspend fun createPlaylist(name: String, isPublic: Boolean, cover: UploadPart?): Long

    suspend fun updatePlaylist(playlistId: Long, name: String): NetworkPlaylistEmpty

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
    override suspend fun getAvailablePlaylists(filters: PlaylistFilters): List<NetworkPlaylist> {
        return generatedApiProvider.withAuthorizedApi { api ->
            playlistApiMapper.mapPlaylists(
                apiExecutor.execute {
                    api.getPlaylistsWithHttpInfo(
                        includeOwned = filters.includeOwned,
                        includeShared = filters.includeShared,
                        includePublic = filters.includePublic,
                    )
                },
            )
        }
    }

    override suspend fun getPlaylist(playlistId: Long): NetworkPlaylistDetails {
        return generatedApiProvider.withAuthorizedApi { api ->
            playlistApiMapper.mapPlaylist(
                apiExecutor.execute {
                    api.getPlaylistWithHttpInfo(playlistId.toInt())
                },
            )
        }
    }

    override suspend fun createPlaylist(name: String, isPublic: Boolean, cover: UploadPart?): Long {
        val currentUser = authSessionManager.requireCurrentUser()
        return generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.createPlaylistWithHttpInfo(
                    ownerId = currentUser.remoteId.toInt(),
                    playlistName = name,
                    isPublic = isPublic,
                    playlistCover = cover?.file,
                )
            }.playlistId.toLong()
        }
    }

    override suspend fun updatePlaylist(playlistId: Long, name: String): NetworkPlaylistEmpty {
        return generatedApiProvider.withAuthorizedApi { api ->
            playlistApiMapper.mapEmptyPlaylist(
                apiExecutor.execute {
                    api.updatePlaylistWithHttpInfo(
                        playlistId = playlistId.toInt(),
                        playlistUpdateRequest = PlaylistUpdateRequest(
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
