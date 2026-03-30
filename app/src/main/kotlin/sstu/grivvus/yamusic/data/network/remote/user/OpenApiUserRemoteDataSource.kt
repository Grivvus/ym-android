package sstu.grivvus.yamusic.data.network.remote.user

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.ServerInfoRepository
import sstu.grivvus.yamusic.data.network.auth.AuthSessionManager
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.mapper.UserApiMapper
import sstu.grivvus.yamusic.data.network.model.NetworkUser
import sstu.grivvus.yamusic.data.network.model.UploadPart
import sstu.grivvus.yamusic.openapi.models.UserChangePassword
import sstu.grivvus.yamusic.openapi.models.UserUpdate

@Singleton
class OpenApiUserRemoteDataSource @Inject constructor(
    private val authSessionManager: AuthSessionManager,
    private val serverInfoRepository: ServerInfoRepository,
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val userApiMapper: UserApiMapper,
) : UserRemoteDataSource {
    override suspend fun getCurrentUser(): NetworkUser {
        val currentUser = authSessionManager.requireCurrentUser()
        return generatedApiProvider.withAuthorizedApi { api ->
            userApiMapper.mapUser(
                apiExecutor.execute {
                    api.getUserByIdWithHttpInfo(currentUser.remoteId.toInt())
                },
            )
        }
    }

    override suspend fun updateCurrentUser(newUsername: String, newEmail: String): NetworkUser {
        val currentUser = authSessionManager.requireCurrentUser()
        return generatedApiProvider.withAuthorizedApi { api ->
            userApiMapper.mapUser(
                apiExecutor.execute {
                    api.changeUserWithHttpInfo(
                        userId = currentUser.remoteId.toInt(),
                        userUpdate = UserUpdate(
                            newUsername = newUsername,
                            newEmail = newEmail,
                        ),
                    )
                },
            )
        }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        val currentUser = authSessionManager.requireCurrentUser()
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.executeUnit {
                api.changePasswordWithHttpInfo(
                    userId = currentUser.remoteId.toInt(),
                    userChangePassword = UserChangePassword(
                        oldPassword = currentPassword,
                        newPassword = newPassword,
                    ),
                )
            }
        }
    }

    override suspend fun uploadAvatar(avatar: UploadPart): String {
        val currentUser = authSessionManager.requireCurrentUser()
        generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.uploadUserAvatarWithHttpInfo(
                    userId = currentUser.remoteId.toInt(),
                    body = avatar.file,
                )
            }
        }
        return serverInfoRepository.avatarUrl(currentUser.remoteId)
    }
}
