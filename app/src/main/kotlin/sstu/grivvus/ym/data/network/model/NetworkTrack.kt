package sstu.grivvus.ym.data.network.model

import androidx.annotation.StringRes
import sstu.grivvus.ym.R

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

@StringRes
fun TrackQuality.toDisplayNameRes(): Int {
    return when (this) {
        TrackQuality.FAST -> R.string.track_quality_fast
        TrackQuality.STANDARD -> R.string.track_quality_standard
        TrackQuality.HIGH -> R.string.track_quality_high
        TrackQuality.LOSSLESS -> R.string.track_quality_lossless
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
