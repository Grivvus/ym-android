package sstu.grivvus.ym.data.network.model

data class NetworkSession(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
)
