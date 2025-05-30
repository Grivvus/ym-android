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
        val token = registerUser(user)
        localDataSource.clearTable()
        localDataSource.insert(
            LocalUser(
                user.username, user.email, token.accessToken
            )
        )
    }

    suspend fun login(user: NetworkUserLogin) {
        val token = loginUser(user)
        localDataSource.clearTable()
        localDataSource.insert(LocalUser(user.username, null, token.accessToken))
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val username = getCurrentUser().username
        changeUserPassword(
            ChangePasswordDto(
                username, currentPassword, newPassword
            )
        )
    }

    suspend fun getCurrentUser(): LocalUser {
        val currentUser = localDataSource.getActiveUser()
        return currentUser
    }
}