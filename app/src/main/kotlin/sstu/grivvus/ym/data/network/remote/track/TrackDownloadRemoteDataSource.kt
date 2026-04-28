package sstu.grivvus.ym.data.network.remote.track

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import sstu.grivvus.ym.data.network.core.ApiBaseUrlProvider
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.ClientApiException
import sstu.grivvus.ym.data.network.core.NotFoundApiException
import sstu.grivvus.ym.data.network.core.ServerApiException
import sstu.grivvus.ym.data.network.core.UnauthorizedApiException
import sstu.grivvus.ym.di.PlaybackHttpClient

data class TrackDownloadMetadata(
    val contentType: String?,
    val contentLength: Long?,
    val eTag: String?,
    val checksumSha256: String?,
    val requestedQuality: String?,
    val resolvedQuality: String?,
)

interface TrackDownloadRemoteDataSource {
    suspend fun headTrack(trackId: Long): TrackDownloadMetadata

    suspend fun downloadTrack(trackId: Long, destinationFile: File)
}

@Singleton
class OkHttpTrackDownloadRemoteDataSource @Inject constructor(
    private val apiBaseUrlProvider: ApiBaseUrlProvider,
    private val apiExecutor: ApiExecutor,
    @param:PlaybackHttpClient private val okHttpClient: OkHttpClient,
) : TrackDownloadRemoteDataSource {
    override suspend fun headTrack(trackId: Long): TrackDownloadMetadata {
        return apiExecutor.executeRaw {
            okHttpClient.newCall(
                Request.Builder()
                    .url(downloadUrl(trackId))
                    .head()
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw response.toTrackDownloadException("Track download metadata request failed")
                }
                TrackDownloadMetadata(
                    contentType = response.header("Content-Type"),
                    contentLength = response.header("Content-Length")?.toLongOrNull(),
                    eTag = response.header("ETag"),
                    checksumSha256 = response.header("X-Track-Checksum-Sha256"),
                    requestedQuality = response.header("X-Track-Quality-Requested"),
                    resolvedQuality = response.header("X-Track-Quality-Resolved"),
                )
            }
        }
    }

    override suspend fun downloadTrack(trackId: Long, destinationFile: File) {
        apiExecutor.executeRaw {
            okHttpClient.newCall(
                Request.Builder()
                    .url(downloadUrl(trackId))
                    .get()
                    .header("Accept", "audio/*")
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw response.toTrackDownloadException("Track download request failed")
                }
                val body = response.body
                body.byteStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun downloadUrl(trackId: Long): String {
        val baseUrl = apiBaseUrlProvider.baseUrl().toHttpUrlOrNull()
            ?: throw IllegalStateException("Track download base URL is invalid")
        return baseUrl.newBuilder()
            .addPathSegments("tracks/$trackId/download")
            .addQueryParameter("quality", DOWNLOAD_QUALITY)
            .build()
            .toString()
    }

    private fun Response.toTrackDownloadException(message: String): Exception {
        val rawBody = body.string()
        return when (code) {
            401 -> UnauthorizedApiException(
                message = "Authentication required",
                rawBody = rawBody,
            )

            404 -> NotFoundApiException(
                message = "Track was not found",
                rawBody = rawBody,
            )

            in 400..499 -> ClientApiException(
                statusCode = code,
                message = message,
                rawBody = rawBody,
            )

            else -> ServerApiException(
                statusCode = code,
                message = message,
                rawBody = rawBody,
            )
        }
    }

    private companion object {
        private const val DOWNLOAD_QUALITY = "standard"
    }
}
