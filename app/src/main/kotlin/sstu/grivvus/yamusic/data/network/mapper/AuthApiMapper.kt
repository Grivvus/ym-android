package sstu.grivvus.yamusic.data.network.mapper

import sstu.grivvus.yamusic.data.network.model.NetworkSession
import sstu.grivvus.yamusic.openapi.models.TokenResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthApiMapper @Inject constructor() {
    fun mapSession(response: TokenResponse): NetworkSession {
        return NetworkSession(
            userId = response.userId.toLong(),
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
        )
    }
}
