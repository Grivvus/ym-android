package sstu.grivvus.yamusic.data

import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import sstu.grivvus.yamusic.data.local.LocalUser
import sstu.grivvus.yamusic.data.local.UserDao
import sstu.grivvus.yamusic.data.network.ChangePasswordDto
import sstu.grivvus.yamusic.data.network.ChangeUserDto
import sstu.grivvus.yamusic.data.network.NetworkUserCreate
import sstu.grivvus.yamusic.data.network.NetworkUserLogin
import sstu.grivvus.yamusic.data.network.changeUser
import sstu.grivvus.yamusic.data.network.changeUserPassword
import sstu.grivvus.yamusic.data.network.getNetworkUser
import sstu.grivvus.yamusic.data.network.loginUser
import sstu.grivvus.yamusic.data.network.registerUser
import sstu.grivvus.yamusic.di.ApplicationScope
import sstu.grivvus.yamusic.di.DefaultDispatcher
import javax.inject.Inject

class UserRepository @Inject constructor(
    val localDataSource: UserDao,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun register(user: NetworkUserCreate) {
        val data = registerUser(user)
        localDataSource.clearTable()
        localDataSource.insert(
            LocalUser(
                data.userId, user.username, user.email,
                data.accessToken, data.refreshToken
            )
        )
    }

    suspend fun login(user: NetworkUserLogin) {
        val data = loginUser(user)
        localDataSource.clearTable()
        localDataSource.insert(
            LocalUser(
                data.userId, user.username, null,
                data.accessToken, data.refreshToken,
            )
        )
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val username = getCurrentUser().username
        changeUserPassword(
            ChangePasswordDto(
                username, currentPassword, newPassword
            )
        )
    }

    suspend fun updateLocalUserFromNetwork(): Unit {
        val localUser = getCurrentUser()
        val networkUser = getNetworkUser(localUser.remoteId)
        localDataSource.update(
            LocalUser(
                networkUser.id, networkUser.username, networkUser.email,
                localUser.access, localUser.refresh
            )
        )
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
        changeUser(user)
        val localUser = localDataSource.getActiveUser()
        localDataSource.clearTable()
        val newUserData = LocalUser(
            localUser.remoteId,
            user.newUsername ?: localUser.username,
            user.newEmail ?: localUser.email,
            localUser.access,
            localUser.refresh
        )
        localDataSource.update(newUserData)
    }
}