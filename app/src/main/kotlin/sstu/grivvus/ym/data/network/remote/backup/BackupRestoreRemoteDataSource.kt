package sstu.grivvus.ym.data.network.remote.backup

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import sstu.grivvus.ym.data.ArchiveOperationState
import sstu.grivvus.ym.data.BackupDownloadProgress
import sstu.grivvus.ym.data.BackupCreationOptions
import sstu.grivvus.ym.data.BackupOperationStatus
import sstu.grivvus.ym.data.RestoreOperationStatus
import sstu.grivvus.ym.data.ServerInfoRepository
import sstu.grivvus.ym.data.network.core.ApiException
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
        onProgress: (BackupDownloadProgress) -> Unit,
    ): String? {
        return apiExecutor.executeRaw {
            val baseUrl = serverInfoRepository.currentBaseUrl().toHttpUrlOrNull()
                ?: throw IllegalStateException("Backup base URL is invalid")
            val url = baseUrl.newBuilder()
                .addPathSegment("backup")
                .addPathSegment(backupId)
                .addPathSegment("download")
                .build()

            val metadata = fetchBackupDownloadMetadata(url)
            val contentLength = metadata.contentLength
            return@executeRaw if (metadata.acceptsRanges && contentLength != null && contentLength > 0L) {
                downloadBackupArchiveInRanges(
                    url = url,
                    metadata = metadata,
                    destinationFile = destinationFile,
                    onProgress = onProgress,
                )
                metadata.suggestedFileName
            } else {
                downloadBackupArchiveInSingleRequest(
                    url = url,
                    destinationFile = destinationFile,
                    onProgress = onProgress,
                )
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

    private fun fetchBackupDownloadMetadata(url: HttpUrl): BackupDownloadMetadata {
        return okHttpClient.newCall(
            Request.Builder()
                .url(url)
                .head()
                .header("Accept", "application/zip")
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.toBackupDownloadException()
            }
            BackupDownloadMetadata(
                contentLength = response.header("Content-Length")?.toLongOrNull(),
                acceptsRanges = response.header("Accept-Ranges")
                    ?.equals("bytes", ignoreCase = true) == true,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                suggestedFileName = extractSuggestedFileName(
                    response.header("Content-Disposition"),
                ),
            )
        }
    }

    private fun downloadBackupArchiveInRanges(
        url: HttpUrl,
        metadata: BackupDownloadMetadata,
        destinationFile: File,
        onProgress: (BackupDownloadProgress) -> Unit,
    ) {
        val totalBytes = requireNotNull(metadata.contentLength)
        RandomAccessFile(destinationFile, "rw").use { file ->
            file.setLength(0L)
        }
        var downloadedBytes = 0L
        onProgress(
            BackupDownloadProgress(
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
            ),
        )
        while (downloadedBytes < totalBytes) {
            val rangeStart = downloadedBytes
            val rangeEnd = min(rangeStart + BACKUP_DOWNLOAD_CHUNK_SIZE_BYTES - 1L, totalBytes - 1L)
            val copiedBytes = executeRangeRequestWithRetry(
                url = url,
                metadata = metadata,
                destinationFile = destinationFile,
                rangeStart = rangeStart,
                rangeEnd = rangeEnd,
                totalBytes = totalBytes,
                onProgress = onProgress,
            )
            if (copiedBytes <= 0L && downloadedBytes < totalBytes) {
                throw IOException("Backup download did not make progress")
            }
            downloadedBytes += copiedBytes
            onProgress(
                BackupDownloadProgress(
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                ),
            )
        }
        val fileSize = destinationFile.length()
        if (fileSize != totalBytes) {
            throw IOException(
                "Backup archive size mismatch: expected $totalBytes bytes, got $fileSize bytes",
            )
        }
    }

    private fun executeRangeRequestWithRetry(
        url: HttpUrl,
        metadata: BackupDownloadMetadata,
        destinationFile: File,
        rangeStart: Long,
        rangeEnd: Long,
        totalBytes: Long,
        onProgress: (BackupDownloadProgress) -> Unit,
    ): Long {
        var lastError: IOException? = null
        repeat(BACKUP_DOWNLOAD_CHUNK_MAX_ATTEMPTS) { attemptIndex ->
            try {
                return executeRangeRequest(
                    url = url,
                    metadata = metadata,
                    destinationFile = destinationFile,
                    rangeStart = rangeStart,
                    rangeEnd = rangeEnd,
                    totalBytes = totalBytes,
                    onProgress = onProgress,
                )
            } catch (error: ApiException) {
                throw error
            } catch (error: IOException) {
                lastError = error
                if (attemptIndex < BACKUP_DOWNLOAD_CHUNK_MAX_ATTEMPTS - 1) {
                    onProgress(
                        BackupDownloadProgress(
                            downloadedBytes = rangeStart,
                            totalBytes = totalBytes,
                        ),
                    )
                }
            }
        }
        throw lastError ?: IOException("Backup download chunk failed")
    }

    private fun executeRangeRequest(
        url: HttpUrl,
        metadata: BackupDownloadMetadata,
        destinationFile: File,
        rangeStart: Long,
        rangeEnd: Long,
        totalBytes: Long,
        onProgress: (BackupDownloadProgress) -> Unit,
    ): Long {
        return okHttpClient.newCall(
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/zip")
                .header("Range", "bytes=$rangeStart-$rangeEnd")
                .build(),
        ).execute().use { response ->
            when (response.code) {
                206 -> {
                    val contentRange = validateContentRange(
                        response = response,
                        expectedStart = rangeStart,
                        expectedEnd = rangeEnd,
                        expectedTotal = totalBytes,
                    )
                    validateBackupValidators(metadata = metadata, response = response)
                    val copiedBytes = copyResponseBodyToFile(
                        response = response,
                        destinationFile = destinationFile,
                        writeOffset = rangeStart,
                        totalBytes = totalBytes,
                        onProgress = onProgress,
                    )
                    val expectedChunkBytes = contentRange.lastByte - contentRange.firstByte + 1L
                    if (copiedBytes != expectedChunkBytes) {
                        throw IOException(
                            "Backup download chunk size mismatch: expected $expectedChunkBytes bytes, " +
                                "got $copiedBytes bytes",
                        )
                    }
                    copiedBytes
                }

                200 -> {
                    if (rangeStart != 0L) {
                        throw IOException("Backup server ignored byte range request")
                    }
                    val copiedBytes = copyResponseBodyToFile(
                        response = response,
                        destinationFile = destinationFile,
                        writeOffset = 0L,
                        totalBytes = totalBytes,
                        onProgress = onProgress,
                    )
                    if (copiedBytes != totalBytes) {
                        throw IOException(
                            "Backup archive size mismatch: expected $totalBytes bytes, got $copiedBytes bytes",
                        )
                    }
                    copiedBytes
                }

                416 -> handleUnsatisfiableRange(
                    response = response,
                    destinationFile = destinationFile,
                    rangeStart = rangeStart,
                    totalBytes = totalBytes,
                )

                else -> {
                    if (!response.isSuccessful) {
                        throw response.toBackupDownloadException()
                    }
                    throw IOException("Unexpected backup download response: HTTP ${response.code}")
                }
            }
        }
    }

    private fun downloadBackupArchiveInSingleRequest(
        url: HttpUrl,
        destinationFile: File,
        onProgress: (BackupDownloadProgress) -> Unit,
    ): String? {
        return okHttpClient.newCall(
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/zip")
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) {
                throw response.toBackupDownloadException()
            }
            val totalBytes = response.header("Content-Length")?.toLongOrNull()
            onProgress(BackupDownloadProgress(downloadedBytes = 0L, totalBytes = totalBytes))
            val copiedBytes = copyResponseBodyToFile(
                response = response,
                destinationFile = destinationFile,
                writeOffset = 0L,
                totalBytes = totalBytes,
                onProgress = onProgress,
            )
            if (totalBytes != null && copiedBytes != totalBytes) {
                throw IOException(
                    "Backup archive size mismatch: expected $totalBytes bytes, got $copiedBytes bytes",
                )
            }
            extractSuggestedFileName(response.header("Content-Disposition"))
        }
    }

    private fun copyResponseBodyToFile(
        response: Response,
        destinationFile: File,
        writeOffset: Long,
        totalBytes: Long?,
        onProgress: (BackupDownloadProgress) -> Unit,
    ): Long {
        val buffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
        var copiedBytes = 0L
        var lastReportedDownloadedBytes = writeOffset
        response.body.byteStream().use { input ->
            RandomAccessFile(destinationFile, "rw").use { output ->
                output.seek(writeOffset)
                while (true) {
                    val readBytes = input.read(buffer)
                    if (readBytes == -1) {
                        break
                    }
                    output.write(buffer, 0, readBytes)
                    copiedBytes += readBytes
                    val downloadedBytes = writeOffset + copiedBytes
                    if (downloadedBytes - lastReportedDownloadedBytes >= PROGRESS_REPORT_STEP_BYTES ||
                        totalBytes != null && downloadedBytes >= totalBytes
                    ) {
                        onProgress(
                            BackupDownloadProgress(
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                            ),
                        )
                        lastReportedDownloadedBytes = downloadedBytes
                    }
                }
            }
        }
        return copiedBytes
    }

    private fun validateContentRange(
        response: Response,
        expectedStart: Long,
        expectedEnd: Long,
        expectedTotal: Long,
    ): ContentRange {
        val contentRange = parseContentRange(response.header("Content-Range"))
            ?: throw IOException("Backup partial response is missing Content-Range")
        if (contentRange.firstByte != expectedStart ||
            contentRange.lastByte < contentRange.firstByte ||
            contentRange.lastByte > expectedEnd ||
            contentRange.totalBytes != expectedTotal
        ) {
            throw IOException("Backup partial response Content-Range does not match the requested range")
        }
        return contentRange
    }

    private fun validateBackupValidators(
        metadata: BackupDownloadMetadata,
        response: Response,
    ) {
        val responseEtag = response.header("ETag")
        if (metadata.etag != null && responseEtag != null && responseEtag != metadata.etag) {
            throw IOException("Backup archive ETag changed during download")
        }
        val responseLastModified = response.header("Last-Modified")
        if (metadata.lastModified != null &&
            responseLastModified != null &&
            responseLastModified != metadata.lastModified
        ) {
            throw IOException("Backup archive Last-Modified changed during download")
        }
    }

    private fun handleUnsatisfiableRange(
        response: Response,
        destinationFile: File,
        rangeStart: Long,
        totalBytes: Long,
    ): Long {
        val serverTotalBytes = parseUnsatisfiableContentRange(
            response.header("Content-Range"),
        )
        if (serverTotalBytes == totalBytes &&
            rangeStart >= totalBytes &&
            destinationFile.length() == totalBytes
        ) {
            return 0L
        }
        throw ClientApiException(
            statusCode = 416,
            message = "Backup download byte range is invalid",
            rawBody = response.body.string(),
        )
    }

    private fun parseContentRange(header: String?): ContentRange? {
        if (header.isNullOrBlank()) {
            return null
        }
        val match = CONTENT_RANGE_REGEX.matchEntire(header.trim()) ?: return null
        val firstByte = match.groupValues[1].toLongOrNull() ?: return null
        val lastByte = match.groupValues[2].toLongOrNull() ?: return null
        val totalBytes = match.groupValues[3].toLongOrNull() ?: return null
        return ContentRange(
            firstByte = firstByte,
            lastByte = lastByte,
            totalBytes = totalBytes,
        )
    }

    private fun parseUnsatisfiableContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) {
            return null
        }
        val match = UNSATISFIABLE_CONTENT_RANGE_REGEX.matchEntire(header.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()
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

            416 -> ClientApiException(
                statusCode = code,
                message = "Backup download byte range is invalid",
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

    private data class BackupDownloadMetadata(
        val contentLength: Long?,
        val acceptsRanges: Boolean,
        val etag: String?,
        val lastModified: String?,
        val suggestedFileName: String?,
    )

    private data class ContentRange(
        val firstByte: Long,
        val lastByte: Long,
        val totalBytes: Long,
    )

    private companion object {
        private const val BACKUP_DOWNLOAD_CHUNK_SIZE_BYTES = 1024L * 1024L
        private const val BACKUP_DOWNLOAD_CHUNK_MAX_ATTEMPTS = 3
        private const val COPY_BUFFER_SIZE_BYTES = 64 * 1024
        private const val PROGRESS_REPORT_STEP_BYTES = 256L * 1024L
        private val FILENAME_UTF8_REGEX = Regex("filename\\*=UTF-8''([^;]+)")
        private val FILENAME_REGEX = Regex("filename=\"([^\"]+)\"")
        private val FALLBACK_FILENAME_REGEX = Regex("filename=([^;]+)")
        private val CONTENT_RANGE_REGEX = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
        private val UNSATISFIABLE_CONTENT_RANGE_REGEX = Regex("bytes \\*/(\\d+)")
    }
}
