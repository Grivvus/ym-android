package sstu.grivvus.yamusic.data

import sstu.grivvus.yamusic.di.IoDispatcher
import sstu.grivvus.yamusic.di.DefaultDispatcher
import sstu.grivvus.yamusic.di.ApplicationScope
import sstu.grivvus.yamusic.data.network.*
import sstu.grivvus.yamusic.data.local.UserDao
import kotlinx.coroutines.*
import sstu.grivvus.yamusic.data.local.LocalUser
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.EmptyCoroutineContext

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
                data.servId, data.username, data.email, data.accessToken
            )
        )
    }

    suspend fun login(user: NetworkUserLogin) {
        val data = loginUser(user)
        localDataSource.clearTable()
        localDataSource.insert(LocalUser(
            data.servId, data.username, data.email, data.accessToken
        ))
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
        val networkUser = getNetworkUser(localUser.servId)
        localDataSource.update(LocalUser(
            networkUser.id, networkUser.username, networkUser.email,
            localUser.token
        ))
    }

    suspend fun getCurrentUser(): LocalUser {
        val currentUser = localDataSource.getActiveUser()
        return currentUser
    }

    suspend fun updateCurrentUserAvatar(uriStr: String) {
        val currentUser = localDataSource.getActiveUser()
        localDataSource.update(LocalUser(
            currentUser.servId,
            currentUser.username, currentUser.email,
            currentUser.token, uriStr,
        ))
    }

    suspend fun applyChanges(user: ChangeUserDto) {
        changeUser(user)
        val localUser = localDataSource.getActiveUser()
        localDataSource.clearTable()
        val newUserData = LocalUser(
            localUser.servId,
            user.newUsername ?: localUser.username,
            user.newEmail ?: localUser.email,
            localUser.token,
        )
        localDataSource.update(newUserData)
    }
}