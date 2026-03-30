package sstu.grivvus.yamusic.data.network.model

enum class TrackQuality {
    FAST, STANDARD, HIGH, LOSSLESS
}

data class NetworkTrack(
    val id: Long,
    val name: String,
    val artistId: Long,
    val albumId: Long,
    val coverUrl: String? = null,
    val qualityPresets: Map<TrackQuality, String> = emptyMap(),
)
