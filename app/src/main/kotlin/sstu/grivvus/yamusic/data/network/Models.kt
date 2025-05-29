package sstu.grivvus.yamusic.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.Date

@Serializable
data class NetworkUserCreate (
    val username: String,
    val email: String?,
    val password: String,
)

@Serializable
data class NetworkUserLogin(
    val username: String,
    val password: String,
)

@Serializable
data class TokenResponse(
    @SerialName("token_type")
    val tokenType: String,
    @SerialName("access_token")
    val accessToken: String,
)