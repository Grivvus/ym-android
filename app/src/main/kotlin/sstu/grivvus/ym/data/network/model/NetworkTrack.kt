package sstu.grivvus.ym.data.network.model

enum class TrackQuality {
    FAST, STANDARD, HIGH, LOSSLESS
}

fun TrackQuality.toQueryValue(): String {
    return when (this) {
        TrackQuality.FAST -> "fast"
        TrackQuality.STANDARD -> "standard"
        TrackQuality.HIGH -> "high"
        TrackQuality.LOSSLESS -> "lossless"
    }
}

fun TrackQuality.toDisplayName(): String {
    return when (this) {
        TrackQuality.FAST -> "Fast"
        TrackQuality.STANDARD -> "Standard"
        TrackQuality.HIGH -> "High"
        TrackQuality.LOSSLESS -> "Lossless"
    }
}

data class NetworkTrack(
    val id: Long,
    val name: String,
    val artistId: Long,
    val albumId: Long,
    val durationMs: Long,
    val coverUrl: String? = null,
    val qualityPresets: Map<TrackQuality, String> = emptyMap(),
)
