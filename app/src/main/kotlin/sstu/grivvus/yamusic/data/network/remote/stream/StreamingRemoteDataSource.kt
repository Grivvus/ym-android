package sstu.grivvus.yamusic.data.network.remote.stream

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import sstu.grivvus.yamusic.data.network.auth.AuthHeaderProvider
import sstu.grivvus.yamusic.data.network.core.ApiBaseUrlProvider
import sstu.grivvus.yamusic.data.network.core.ApiExecutor
import sstu.grivvus.yamusic.data.network.core.ClientApiException
import sstu.grivvus.yamusic.data.network.core.ServerApiException
import sstu.grivvus.yamusic.data.network.core.UnauthorizedApiException
import sstu.grivvus.yamusic.data.network.model.StreamInfo
import sstu.grivvus.yamusic.di.PlaybackHttpClient
import javax.inject.Inject
import javax.inject.Singleton

interface StreamingRemoteDataSource {
    suspend fun headTrack(trackId: Long, quality: String? = null): StreamInfo

    fun streamUrl(trackId: Long, quality: String? = null): String

    suspend fun streamHeaders(): Map<String, String>
}

@Singleton
class OkHttpStreamingRemoteDataSource @Inject constructor(
    private val apiBaseUrlProvider: ApiBaseUrlProvider,
    private val authHeaderProvider: AuthHeaderProvider,
    private val apiExecutor: ApiExecutor,
    @PlaybackHttpClient private val okHttpClient: OkHttpClient,
) : StreamingRemoteDataSource {
    override suspend fun headTrack(trackId: Long, quality: String?): StreamInfo {
        val url = streamUrl(trackId, quality)
        return apiExecutor.executeRaw {
            okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .head()
                    .build(),
            ).execute().use { response ->
                val rawBody = response.body?.string()
                when {
                    response.isSuccessful -> StreamInfo(
                        url = url,
                        headers = streamHeaders(),
                        contentType = response.header("Content-Type"),
                        contentLength = response.header("Content-Length")?.toLongOrNull(),
                    )

                    response.code == 401 -> throw UnauthorizedApiException(
                        message = "Unauthorized",
                        rawBody = rawBody,
                    )

                    response.code in 400..499 -> throw ClientApiException(
                        statusCode = response.code,
                        message = "Stream metadata request failed",
                        rawBody = rawBody,
                    )

                    else -> throw ServerApiException(
                        statusCode = response.code,
                        message = "Stream metadata request failed",
                        rawBody = rawBody,
                    )
                }
            }
        }
    }

    override fun streamUrl(trackId: Long, quality: String?): String {
        val baseUrl = apiBaseUrlProvider.baseUrl().toHttpUrlOrNull()
            ?: throw IllegalStateException("Streaming base URL is invalid")
        return baseUrl.newBuilder()
            .addPathSegments("tracks/$trackId/stream")
            .apply {
                // backend will use 'standard' quality is not set
                if (!quality.isNullOrBlank()) {
                    addQueryParameter("quality", quality)
                }
            }
            .build()
            .toString()
    }

    override suspend fun streamHeaders(): Map<String, String> {
        val authorizationHeader = authHeaderProvider.authorizationHeaderOrNull()
        return if (authorizationHeader.isNullOrBlank()) {
            emptyMap()
        } else {
            mapOf("Authorization" to authorizationHeader)
        }
    }
}
