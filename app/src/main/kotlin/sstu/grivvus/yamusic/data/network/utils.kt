package sstu.grivvus.yamusic.data.network

import androidx.core.net.toUri
import kotlinx.serialization.json.Json
import sstu.grivvus.yamusic.data.local.AudioTrack

fun responseToToken(responseBody: String): TokenResponse {
    return Json.decodeFromString(responseBody)
}

fun responseToRemoteTrackReturn(responseBody: String): List<RemoteTrackReturn> {
    return Json.decodeFromString(responseBody)
}

fun netTrackToLocal(track: RemoteTrackReturn): AudioTrack {
    return AudioTrack(
        servId = track.id,
        title = track.name,
        uri = track.url.toUri(),
        album = track.album,
        artist = track.artist ?: "Unknown"
    )
}