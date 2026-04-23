package sstu.grivvus.ym.data.network.model

data class NetworkPlaylist(
    val id: Long,
    val name: String,
    val trackIds: List<Long> = emptyList(),
)

data class NetworkPlaylistEmpty(
    val id: Long,
    val name: String,
)
