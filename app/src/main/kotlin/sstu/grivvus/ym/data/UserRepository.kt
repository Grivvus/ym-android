package sstu.grivvus.ym.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import sstu.grivvus.ym.data.local.LocalUser
import sstu.grivvus.ym.data.local.UserDao
import sstu.grivvus.ym.data.network.auth.AuthSessionManager
import sstu.grivvus.ym.data.network.model.UploadPart
import sstu.grivvus.ym.data.network.remote.auth.AuthRemoteDataSource
import sstu.grivvus.ym.data.network.remote.user.UserRemoteDataSource
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class UserRepository @Inject constructor(
    private val authRemoteDataSource: AuthRemoteDataSource,
    private val userRemoteDataSource: UserRemoteDataSource,
    private val authSessionManager: AuthSessionManager,
    private val serverInfoRepository: ServerInfoRepository,
    private val userDao: UserDao,
    @param:ApplicationContext private val context: Context,
) {
    suspend fun register(username: String, password: String) {
        val session = authRemoteDataSource.register(
            username = username,
            password = password,
        )
        authSessionManager.startSession(session)
        authSessionManager.updateCurrentUser(
            LocalUser(
                remoteId = session.userId,
                username = username,
                email = null,
                access = session.accessToken,
                refresh = session.refreshToken,
                isSuperuser = false,
            ),
        )
    }

    suspend fun login(username: String, password: String) {
        val session = authRemoteDataSource.login(
            username = username,
            password = password,
        )
        authSessionManager.startSession(session)
        authSessionManager.updateCurrentUser(
            LocalUser(
                remoteId = session.userId,
                username = username,
                email = null,
                access = session.accessToken,
                refresh = session.refreshToken,
                isSuperuser = false,
            ),
        )
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        if (newPassword.length < 6) {
            throw IOException("password's length should be 6 symbols or more")
        }
        userRemoteDataSource.changePassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
        )
    }

    suspend fun requestPasswordReset(email: String): String {
        return authRemoteDataSource.requestPasswordReset(email = email)
    }

    suspend fun confirmPasswordReset(email: String, code: String, newPassword: String): String {
        if (newPassword.length < 6) {
            throw IOException("password's length should be 6 symbols or more")
        }
        return authRemoteDataSource.confirmPasswordReset(
            email = email,
            code = code,
            newPassword = newPassword,
        )
    }

    suspend fun updateLocalUserFromNetwork() {
        val localUser = authSessionManager.requireCurrentUser()
        val remoteUser = userRemoteDataSource.getCurrentUser()
        authSessionManager.updateCurrentUser(
            LocalUser(
                remoteId = localUser.remoteId,
                username = remoteUser.username,
                email = remoteUser.email,
                access = localUser.access,
                refresh = localUser.refresh,
                isSuperuser = remoteUser.isSuperuser,
                avatarUri = buildRemoteAvatarUri(localUser.remoteId),
            ),
        )
    }

    suspend fun getCurrentUser(): LocalUser? {
        return authSessionManager.getCurrentUser()
    }

    suspend fun requireCurrentUser(): LocalUser {
        return authSessionManager.requireCurrentUser()
    }

    fun observeCurrentUser(): Flow<LocalUser?> {
        return userDao.observeActiveUser()
    }

    suspend fun logout() {
        authSessionManager.logout()
    }

    suspend fun updateCurrentUserAvatar(uriStr: String) {
        val currentUser = authSessionManager.requireCurrentUser()
        val selectedAvatarUri = uriStr.toUri()
        val tempFile = createTempAvatarFile(selectedAvatarUri)
        try {
            userRemoteDataSource.uploadAvatar(
                UploadPart(
                    file = tempFile,
                    mimeType = context.contentResolver.getType(selectedAvatarUri),
                ),
            )
            authSessionManager.updateCurrentUser(
                LocalUser(
                    currentUser.remoteId,
                    currentUser.username, currentUser.email,
                    currentUser.access, currentUser.refresh,
                    currentUser.isSuperuser,
                    buildRemoteAvatarUri(currentUser.remoteId, cacheBust = true),
                ),
            )
        } finally {
            tempFile.delete()
        }
    }

    suspend fun applyChanges(newUsername: String?, newEmail: String?) {
        val localUser = authSessionManager.requireCurrentUser()
        val targetUsername = newUsername ?: localUser.username
        val targetEmail = newEmail ?: (localUser.email ?: "")

        val remoteUser = userRemoteDataSource.updateCurrentUser(
            newUsername = targetUsername,
            newEmail = targetEmail,
        )

        val newUserData = LocalUser(
            localUser.remoteId,
            remoteUser.username,
            remoteUser.email,
            localUser.access,
            localUser.refresh,
            remoteUser.isSuperuser,
            buildRemoteAvatarUri(remoteUser.id, cacheBust = true),
        )
        authSessionManager.updateCurrentUser(newUserData)
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
        val baseUrl = serverInfoRepository.avatarUrl(userId)
        val fullUrl = if (cacheBust) {
            "$baseUrl?ts=${System.currentTimeMillis()}"
        } else {
            baseUrl
        }
        return fullUrl.toUri()
    }
}
