package sstu.grivvus.yamusic.data.network.model

data class NetworkPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val trackIds: List<Long> = emptyList(),
)
