package sstu.grivvus.yamusic.data.network.remote.stream

import sstu.grivvus.yamusic.data.network.model.StreamInfo

interface StreamingRemoteDataSource {
    suspend fun headTrack(trackId: Long, quality: String? = null): StreamInfo

    fun streamUrl(trackId: Long, quality: String? = null): String

    suspend fun streamHeaders(): Map<String, String>
}
