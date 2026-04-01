package sstu.grivvus.ym.data.network.model

data class NetworkAlbum(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val trackIds: List<Long> = emptyList(),
)
