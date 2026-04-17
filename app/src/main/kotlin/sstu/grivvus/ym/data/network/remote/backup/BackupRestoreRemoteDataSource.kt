package sstu.grivvus.ym.data.network.remote.backup

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import sstu.grivvus.ym.data.BackupCreationOptions
import sstu.grivvus.ym.data.RestoreOperationState
import sstu.grivvus.ym.data.RestoreOperationStatus
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.ClientApiException
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.core.ServerApiException
import sstu.grivvus.ym.data.network.core.UnauthorizedApiException
import sstu.grivvus.ym.di.ArchiveTransferHttpClient
import sstu.grivvus.ym.openapi.models.RestoreStatusResponse

@Singleton
class BackupRestoreRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val serverInfoRepository: ServerInfoRepository,
    @param:ArchiveTransferHttpClient private val okHttpClient: OkHttpClient,
) {
    suspend fun downloadBackupArchive(
        options: BackupCreationOptions,
        destinationFile: File,
    ): String? {
        return apiExecutor.executeRaw {
            val baseUrl = serverInfoRepository.currentBaseUrl().toHttpUrlOrNull()
                ?: throw IllegalStateException("Backup base URL is invalid")
            val url = baseUrl.newBuilder()
                .addPathSegments("backup")
                .addQueryParameter("include_images", options.includeImages.toString())
                .addQueryParameter(
                    "include_transcoded_tracks",
                    options.includeTranscodedTracks.toString(),
                )
                .build()

            okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build(),
            ).execute().use { response ->
                val responseBody = response.body
                when {
                    response.isSuccessful -> {
                        responseBody.byteStream().use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractSuggestedFileName(response.header("Content-Disposition"))
                    }

                    response.code == 401 -> throw UnauthorizedApiException(
                        message = "Authentication required",
                        rawBody = responseBody.string(),
                    )

                    response.code == 403 -> throw ClientApiException(
                        statusCode = response.code,
                        message = "Superuser access required",
                        rawBody = responseBody.string(),
                    )

                    response.code in 400..499 -> throw ClientApiException(
                        statusCode = response.code,
                        message = "Backup request failed",
                        rawBody = responseBody.string(),
                    )

                    else -> throw ServerApiException(
                        statusCode = response.code,
                        message = "Backup request failed",
                        rawBody = responseBody.string(),
                    )
                }
            }
        }
    }

    suspend fun startRestore(archiveFile: File): String {
        return generatedApiProvider.withAuthorizedApi { api ->
            apiExecutor.execute {
                api.restoreWithHttpInfo(body = archiveFile)
            }
        }
    }

    suspend fun getRestoreStatus(restoreId: String): RestoreOperationStatus {
        return generatedApiProvider.withAuthorizedApi { api ->
            val response = apiExecutor.execute {
                api.getRestoreStatusWithHttpInfo(restoreId = restoreId)
            }
            RestoreOperationStatus(
                restoreId = response.restoreId,
                state = response.status.toDomainState(),
                errorMessage = response.error,
            )
        }
    }

    private fun extractSuggestedFileName(contentDisposition: String?): String? {
        if (contentDisposition.isNullOrBlank()) {
            return null
        }
        val encodedName = FILENAME_UTF8_REGEX.find(contentDisposition)?.groupValues?.getOrNull(1)
        if (!encodedName.isNullOrBlank()) {
            return URLDecoder.decode(encodedName, StandardCharsets.UTF_8.name())
        }
        return FILENAME_REGEX.find(contentDisposition)?.groupValues?.getOrNull(1)
            ?: FALLBACK_FILENAME_REGEX.find(contentDisposition)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun RestoreStatusResponse.Status.toDomainState(): RestoreOperationState {
        return when (this) {
            RestoreStatusResponse.Status.pending -> RestoreOperationState.PENDING
            RestoreStatusResponse.Status.started -> RestoreOperationState.STARTED
            RestoreStatusResponse.Status.finished -> RestoreOperationState.FINISHED
            RestoreStatusResponse.Status.error -> RestoreOperationState.ERROR
        }
    }

    private companion object {
        private val FILENAME_UTF8_REGEX = Regex("filename\\*=UTF-8''([^;]+)")
        private val FILENAME_REGEX = Regex("filename=\"([^\"]+)\"")
        private val FALLBACK_FILENAME_REGEX = Regex("filename=([^;]+)")
    }
}
