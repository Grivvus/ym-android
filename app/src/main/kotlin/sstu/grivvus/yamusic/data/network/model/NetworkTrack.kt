package sstu.grivvus.yamusic.data.network.model

data class NetworkTrack(
    val id: Long,
    val name: String,
    val artistId: Long,
    val coverUrl: String? = null,
    val qualityPresets: Map<String, String> = emptyMap(),
)
