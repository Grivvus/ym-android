package sstu.grivvus.yamusic.data.network.model

data class NetworkSession(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
)
