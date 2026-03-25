package sstu.grivvus.yamusic.data.network.remote.stream

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import sstu.grivvus.yamusic.data.network.auth.AuthHeaderProvider
import sstu.grivvus.yamusic.data.network.core.ApiBaseUrlProvider
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.model.StreamInfo

@Singleton
class OkHttpStreamingRemoteDataSource @Inject constructor(
    private val apiBaseUrlProvider: ApiBaseUrlProvider,
    private val authHeaderProvider: AuthHeaderProvider,
    private val apiExecutor: ApiExecutor,
    private val okHttpClient: OkHttpClient,
) : StreamingRemoteDataSource {
    override suspend fun headTrack(trackId: Long, quality: String?): StreamInfo {
        return TODO("Implement HEAD /stream request with auth-aware transport")
    }

    override fun streamUrl(trackId: Long, quality: String?): String {
        return TODO("Build streaming URL for Media3/OkHttp transport")
    }

    override suspend fun streamHeaders(): Map<String, String> {
        return TODO("Return auth headers for protected stream requests")
    }
}
