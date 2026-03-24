package sstu.grivvus.yamusic.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.local.UserDao
import sstu.grivvus.yamusic.data.network.ChangeUserDto
import sstu.grivvus.yamusic.data.network.NetworkUserCreate
import sstu.grivvus.yamusic.data.network.NetworkUserLogin
import sstu.grivvus.yamusic.data.network.OpenApiNetworkClient
import sstu.grivvus.yamusic.di.ApplicationScope
import sstu.grivvus.yamusic.di.DefaultDispatcher
import sstu.grivvus.yamusic.getAvatarUrl
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UserRepository @Inject constructor(
    val localDataSource: UserDao,
    private val networkClient: OpenApiNetworkClient,
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun register(user: NetworkUserCreate) {
        val data = networkClient.register(user)
        localDataSource.clearTable()
        localDataSource.insert(
            LocalUser(
                data.userId, user.username, user.email,
                data.accessToken, data.refreshToken
            )
        )
    }

    suspend fun login(user: NetworkUserLogin) {
        val data = networkClient.login(user)
        localDataSource.clearTable()
        localDataSource.insert(
            LocalUser(
                data.userId, user.username, null,
                data.accessToken, data.refreshToken,
            )
        )
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        if (newPassword.length < 6) {
            throw IOException("password's length should be 6 symbols or more")
        }
        val localUser = localDataSource.getActiveUser()
        networkClient.changePassword(
            userId = localUser!!.remoteId,
            currentPassword = currentPassword,
            newPassword = newPassword,
            accessToken = localUser.access,
        )
    }

    suspend fun updateLocalUserFromNetwork(): Unit {
        val localUser = localDataSource.getActiveUser()
        assert(localUser != null)
        val remoteUser = networkClient.getUserById(
            userId = localUser!!.remoteId,
            accessToken = localUser.access,
        )
        localDataSource.update(
            LocalUser(
                remoteId = localUser.remoteId,
                username = remoteUser.username,
                email = remoteUser.email,
                access = localUser.access,
                refresh = localUser.refresh,
                avatarUri = buildRemoteAvatarUri(localUser.remoteId),
            ),
        )
    }

    suspend fun getCurrentUser(): LocalUser? {
        val currentUser = localDataSource.getActiveUser()
        return currentUser
    }

    suspend fun updateCurrentUserAvatar(uriStr: String) {
        val currentUser = localDataSource.getActiveUser()
        assert(currentUser != null)
        val selectedAvatarUri = uriStr.toUri()
        val tempFile = createTempAvatarFile(selectedAvatarUri)
        try {
            networkClient.uploadUserAvatar(
                userId = currentUser!!.remoteId,
                avatarFile = tempFile,
                accessToken = currentUser.access,
            )
            localDataSource.update(
                LocalUser(
                    currentUser.remoteId,
                    currentUser.username, currentUser.email,
                    currentUser.access, currentUser.refresh,
                    buildRemoteAvatarUri(currentUser.remoteId, cacheBust = true),
                ),
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun applyChanges(user: ChangeUserDto) {
        val localUser = localDataSource.getActiveUser()
        assert(localUser != null)
        val targetUsername = user.newUsername ?: localUser!!.username
        val targetEmail = user.newEmail ?: (localUser!!.email ?: "")

        val remoteUser = networkClient.changeUser(
            userId = localUser!!.remoteId,
            newUsername = targetUsername,
            newEmail = targetEmail,
            accessToken = localUser.access,
        )

        val newUserData = LocalUser(
            localUser.remoteId,
            remoteUser.username,
            remoteUser.email,
            localUser.access,
            localUser.refresh,
            buildRemoteAvatarUri(remoteUser.id, cacheBust = true),
        )
        localDataSource.update(newUserData)
    }

    private fun createTempAvatarFile(avatarUri: Uri): File {
        val extension = guessFileExtension(avatarUri)
        val tempFile = File.createTempFile("avatar_upload_", extension, context.cacheDir)
        context.contentResolver.openInputStream(avatarUri).use { input ->
            if (input == null) {
                throw IOException("Unable to open selected avatar file")
            }
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun guessFileExtension(avatarUri: Uri): String {
        val type = context.contentResolver.getType(avatarUri).orEmpty()
        return when {
            type.endsWith("/jpeg") || type.endsWith("/jpg") -> ".jpg"
            type.endsWith("/png") -> ".png"
            type.endsWith("/webp") -> ".webp"
            type.endsWith("/gif") -> ".gif"
            else -> ".tmp"
        }
    }

    private fun buildRemoteAvatarUri(userId: Long, cacheBust: Boolean = false): Uri {
        val baseUrl = getAvatarUrl(userId)
        val fullUrl = if (cacheBust) {
            "$baseUrl?ts=${System.currentTimeMillis()}"
        } else {
            baseUrl
        }
        return fullUrl.toUri()
    }
}
