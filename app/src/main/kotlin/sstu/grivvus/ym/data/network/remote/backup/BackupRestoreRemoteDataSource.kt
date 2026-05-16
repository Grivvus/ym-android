package sstu.grivvus.ym.data.network.remote.backup

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import sstu.grivvus.ym.data.ArchiveOperationState
import sstu.grivvus.ym.data.BackupCreationOptions
import sstu.grivvus.ym.data.BackupOperationStatus
import sstu.grivvus.ym.data.RestoreOperationStatus
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.network.core.ApiExecutor
import sstu.grivvus.ym.data.network.core.ClientApiException
import sstu.grivvus.ym.data.network.core.ConflictApiException
import sstu.grivvus.ym.data.network.core.GeneratedApiProvider
import sstu.grivvus.ym.data.network.core.NotFoundApiException
import sstu.grivvus.ym.data.network.core.ServerApiException
import sstu.grivvus.ym.data.network.core.UnauthorizedApiException
import sstu.grivvus.ym.di.ArchiveTransferHttpClient
import sstu.grivvus.ym.openapi.models.BackupStatusResponse
import sstu.grivvus.ym.openapi.models.OperationStatus
import sstu.grivvus.ym.openapi.models.RestoreStatusResponse

@Singleton
class BackupRestoreRemoteDataSource @Inject constructor(
    private val generatedApiProvider: GeneratedApiProvider,
    private val apiExecutor: ApiExecutor,
    private val serverInfoRepository: ServerInfoRepository,
    @param:ArchiveTransferHttpClient private val okHttpClient: OkHttpClient,
) {
    suspend fun startBackup(options: BackupCreationOptions): BackupOperationStatus {
        return generatedApiProvider.withAuthorizedApi { api ->
            val response = apiExecutor.execute {
                api.backupWithHttpInfo(
                    includeImages = options.includeImages,
                    includeTranscodedTracks = options.includeTranscodedTracks,
                )
            }
            response.toDomainStatus()
        }
    }

    suspend fun getBackupStatus(backupId: String): BackupOperationStatus {
        return generatedApiProvider.withAuthorizedApi { api ->
            val response = apiExecutor.execute {
                api.getBackupStatusWithHttpInfo(backupId = backupId)
            }
            response.toDomainStatus()
        }
    }

    suspend fun downloadBackupArchive(
        backupId: String,
        destinationFile: File,
    ): String? {
        return apiExecutor.executeRaw {
            val baseUrl = serverInfoRepository.currentBaseUrl().toHttpUrlOrNull()
                ?: throw IllegalStateException("Backup base URL is invalid")
            val url = baseUrl.newBuilder()
                .addPathSegment("backup")
                .addPathSegment(backupId)
                .addPathSegment("download")
                .build()

            okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/zip")
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw response.toBackupDownloadException()
                }
                response.body.byteStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                extractSuggestedFileName(response.header("Content-Disposition"))
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
            response.toDomainStatus()
        }
    }

    private fun BackupStatusResponse.toDomainStatus(): BackupOperationStatus {
        return BackupOperationStatus(
            backupId = backupId,
            state = status.toDomainState(),
            includeImages = includeImages,
            includeTranscodedTracks = includeTranscodedTracks,
            sizeBytes = sizeBytes,
            errorMessage = error,
        )
    }

    private fun RestoreStatusResponse.toDomainStatus(): RestoreOperationStatus {
        return RestoreOperationStatus(
            restoreId = restoreId,
            state = status.toDomainState(),
            errorMessage = error,
        )
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

    private fun Response.toBackupDownloadException(): Exception {
        val rawBody = body.string()
        return when (code) {
            401 -> UnauthorizedApiException(
                message = "Authentication required",
                rawBody = rawBody,
            )

            403 -> ClientApiException(
                statusCode = code,
                message = "Superuser access required",
                rawBody = rawBody,
            )

            404 -> NotFoundApiException(
                message = "Backup operation not found",
                rawBody = rawBody,
            )

            409 -> ConflictApiException(
                message = "Backup operation is not finished yet",
                rawBody = rawBody,
            )

            in 400..499 -> ClientApiException(
                statusCode = code,
                message = "Backup download request failed",
                rawBody = rawBody,
            )

            else -> ServerApiException(
                statusCode = code,
                message = "Backup download request failed",
                rawBody = rawBody,
            )
        }
    }

    private fun OperationStatus.toDomainState(): ArchiveOperationState {
        return when (this) {
            OperationStatus.pending -> ArchiveOperationState.PENDING
            OperationStatus.started -> ArchiveOperationState.STARTED
            OperationStatus.finished -> ArchiveOperationState.FINISHED
            OperationStatus.error -> ArchiveOperationState.ERROR
        }
    }

    private companion object {
        private val FILENAME_UTF8_REGEX = Regex("filename\\*=UTF-8''([^;]+)")
        private val FILENAME_REGEX = Regex("filename=\"([^\"]+)\"")
        private val FALLBACK_FILENAME_REGEX = Regex("filename=([^;]+)")
    }
}
