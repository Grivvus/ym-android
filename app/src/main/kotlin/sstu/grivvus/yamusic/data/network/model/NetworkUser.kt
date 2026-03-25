package sstu.grivvus.yamusic.data.network.model

data class NetworkUser(
    val id: Long,
    val username: String,
    val email: String?,
    val avatarUrl: String? = null,
)
