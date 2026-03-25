package sstu.grivvus.yamusic.data.network.mapper

import javax.inject.Inject
import javax.inject.Singleton
import sstu.grivvus.yamusic.data.network.model.NetworkTrack
import sstu.grivvus.yamusic.openapi.models.TrackMetadata

@Singleton
class TrackApiMapper @Inject constructor() {
    fun mapTrack(response: TrackMetadata): NetworkTrack {
        return TODO("Map generated track response to internal track model")
    }

    fun mapTracks(response: List<TrackMetadata>): List<NetworkTrack> {
        return TODO("Map track list response to internal track models")
    }
}
