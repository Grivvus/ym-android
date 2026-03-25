package sstu.grivvus.yamusic.data.network.remote.user

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.GeneratedApiProvider
import sstu.grivvus.yamusic.data.network.mapper.UserApiMapper
import sstu.grivvus.yamusic.data.network.model.NetworkUser
import sstu.grivvus.yamusic.data.network.model.UploadPart

@Singleton
class OpenApiUserRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val userApiMapper: UserApiMapper,
) : UserRemoteDataSource {
    override suspend fun getCurrentUser(): NetworkUser {
        return TODO("Implement current user fetch via generated OpenAPI client")
    }

    override suspend fun updateCurrentUser(newUsername: String, newEmail: String): NetworkUser {
        return TODO("Implement current user update via generated OpenAPI client")
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        TODO("Implement password change via generated OpenAPI client")
    }

    override suspend fun uploadAvatar(avatar: UploadPart): String {
        return TODO("Implement avatar upload via generated OpenAPI client")
    }
}
