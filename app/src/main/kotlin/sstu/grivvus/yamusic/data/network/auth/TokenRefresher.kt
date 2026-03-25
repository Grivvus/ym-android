package sstu.grivvus.yamusic.data.network.auth

import sstu.grivvus.yamusic.data.network.model.NetworkSession

interface TokenRefresher {
    suspend fun refresh(refreshToken: String): NetworkSession
}
