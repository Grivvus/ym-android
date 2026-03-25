package sstu.grivvus.yamusic.data.network.remote.auth

import sstu.grivvus.yamusic.data.network.model.NetworkSession

interface AuthRemoteDataSource {
    suspend fun login(username: String, password: String): NetworkSession

    suspend fun register(username: String, email: String?, password: String): NetworkSession

    suspend fun refresh(refreshToken: String): NetworkSession
}
