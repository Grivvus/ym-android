package sstu.grivvus.yamusic.data.network.mapper

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.model.NetworkSession
import sstu.grivvus.yamusic.openapi.models.TokenResponse

@Singleton
class AuthApiMapper @Inject constructor() {
    fun mapSession(response: TokenResponse): NetworkSession {
        return TODO("Map token response to internal session model")
    }
}
