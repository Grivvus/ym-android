package sstu.grivvus.ym.data.network.model

data class NetworkArtist(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val albumIds: List<Long> = emptyList(),
)
