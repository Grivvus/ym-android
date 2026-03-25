package sstu.grivvus.yamusic.data.network.remote.user

import sstu.grivvus.yamusic.data.network.model.NetworkUser
import sstu.grivvus.yamusic.data.network.model.UploadPart

interface UserRemoteDataSource {
    suspend fun getCurrentUser(): NetworkUser

    suspend fun updateCurrentUser(newUsername: String, newEmail: String): NetworkUser

    suspend fun changePassword(currentPassword: String, newPassword: String)

    suspend fun uploadAvatar(avatar: UploadPart): String
}
