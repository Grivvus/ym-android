package sstu.grivvus.yamusic.data

import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.local.UserDao
import sstu.grivvus.yamusic.data.network.ChangeUserDto
import sstu.grivvus.yamusic.data.network.NetworkUserCreate
import sstu.grivvus.yamusic.data.network.NetworkUserLogin
import sstu.grivvus.yamusic.data.network.OpenApiNetworkClient
import sstu.grivvus.yamusic.di.ApplicationScope
import sstu.grivvus.yamusic.di.DefaultDispatcher
import javax.inject.Inject

class UserRepository @Inject constructor(
    val localDataSource: UserDao,
    private val networkClient: OpenApiNetworkClient,
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
    }

    suspend fun updateLocalUserFromNetwork(): Unit {
        // Network profile endpoint is temporary disabled.
    }

    suspend fun getCurrentUser(): LocalUser {
        val currentUser = localDataSource.getActiveUser()
        return currentUser
    }

    suspend fun updateCurrentUserAvatar(uriStr: String) {
        val currentUser = localDataSource.getActiveUser()
        localDataSource.update(
            LocalUser(
                currentUser.remoteId,
                currentUser.username, currentUser.email,
                currentUser.access, currentUser.refresh,
                uriStr.toUri(),
            )
        )
    }

    suspend fun applyChanges(user: ChangeUserDto) {
        val localUser = localDataSource.getActiveUser()
        val newUserData = LocalUser(
            localUser.remoteId,
            user.newUsername ?: localUser.username,
            user.newEmail ?: localUser.email,
            localUser.access,
            localUser.refresh,
            localUser.avatarUri,
        )
        localDataSource.update(newUserData)
    }
}
