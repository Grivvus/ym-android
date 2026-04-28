package sstu.grivvus.ym.playback.model

import android.net.Uri
import sstu.grivvus.ym.data.network.model.TrackQuality

data class PlayableTrack(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val artistId: Long? = null,
    val artistName: String? = null,
    val albumId: Long? = null,
    val albumName: String? = null,
    val artworkUri: Uri? = null,
    val durationMs: Long? = null,
    val localPath: String? = null,
    val qualityUris: Map<TrackQuality, Uri> = emptyMap(),
)
