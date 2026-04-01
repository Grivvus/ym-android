package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.network.model.NetworkSession
import sstu.grivvus.ym.openapi.models.TokenResponse
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
