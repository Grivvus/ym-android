package sstu.grivvus.ym.data.network.auth

import sstu.grivvus.ym.data.network.model.NetworkSession

interface TokenRefresher {
    suspend fun refresh(refreshToken: String): NetworkSession
}
