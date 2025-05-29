package sstu.grivvus.yamusic.data

import sstu.grivvus.yamusic.di.IoDispatcher
import sstu.grivvus.yamusic.di.DefaultDispatcher
import sstu.grivvus.yamusic.di.ApplicationScope
import sstu.grivvus.yamusic.data.network.*
import sstu.grivvus.yamusic.data.local.UserDao
import kotlinx.coroutines.*
import sstu.grivvus.yamusic.data.local.LocalUser
import javax.inject.Inject

class UserRepository @Inject constructor(
    private val localDataSource: UserDao,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun register(user: NetworkUserCreate) {
        val token = registerUser(user)
        localDataSource.insert(LocalUser(
            user.username, user.email, token.accessToken, user.password
        ))
    }

    suspend fun login(user: NetworkUserLogin) {
        val token = loginUser(user)
        localDataSource.updateToken(user.username, token.accessToken)
    }
}