package sstu.grivvus.ym.data.network.model

data class NetworkUser(
    val id: Long,
    val username: String,
    val email: String?,
    val isSuperuser: Boolean = false,
    val avatarUrl: String? = null,
)
