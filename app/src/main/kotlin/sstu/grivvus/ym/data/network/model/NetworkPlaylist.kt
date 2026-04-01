package sstu.grivvus.ym.data.network.model

data class NetworkPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val trackIds: List<Long> = emptyList(),
)
