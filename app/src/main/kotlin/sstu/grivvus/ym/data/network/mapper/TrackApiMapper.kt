package sstu.grivvus.ym.data.network.mapper

import sstu.grivvus.ym.data.network.model.NetworkTrack
import sstu.grivvus.ym.data.network.model.TrackQuality
import sstu.grivvus.ym.openapi.models.TrackMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackApiMapper @Inject constructor() {
    fun mapTrack(response: TrackMetadata): NetworkTrack {
        return NetworkTrack(
            id = response.trackId.toLong(), name = response.name,
            artistId = response.artistId.toLong(), albumId = response.albumId.toLong(),
            durationMs = response.durationMs.toLong(),
            qualityPresets = response.toQualityPresets(),
        )
    }

    fun mapTracks(response: List<TrackMetadata>): List<NetworkTrack> {
        return response.map { mapTrack(it) }
    }

    private fun TrackMetadata.toQualityPresets(): Map<TrackQuality, String> {
        return listOfNotNull(
            trackFastPreset?.let { PRESET_FAST to it },
            trackStandardPreset?.let { PRESET_STANDARD to it },
            trackHighPreset?.let { PRESET_HIGH to it },
            trackLosslessPreset?.let { PRESET_LOSSLESS to it },
        ).toMap()
    }

    private companion object {
        val PRESET_FAST = TrackQuality.FAST
        val PRESET_STANDARD = TrackQuality.STANDARD
        val PRESET_HIGH = TrackQuality.HIGH
        val PRESET_LOSSLESS = TrackQuality.LOSSLESS
    }
}
