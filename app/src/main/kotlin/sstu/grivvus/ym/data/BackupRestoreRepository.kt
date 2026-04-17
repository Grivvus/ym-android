package sstu.grivvus.ym.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import sstu.grivvus.ym.data.network.remote.backup.BackupRestoreRemoteDataSource
import sstu.grivvus.ym.di.IoDispatcher

data class BackupCreationOptions(
    val includeImages: Boolean = true,
    val includeTranscodedTracks: Boolean = true,
)

data class DownloadedBackupArchive(
    val file: File,
    val suggestedFileName: String,
)

enum class RestoreOperationState {
    PENDING,
    STARTED,
    FINISHED,
    ERROR,
}

data class RestoreOperationStatus(
    val restoreId: String,
    val state: RestoreOperationState,
    val errorMessage: String? = null,
) {
    val isTerminal: Boolean
        get() = state == RestoreOperationState.FINISHED || state == RestoreOperationState.ERROR
}

@Singleton
class BackupRestoreRepository @Inject constructor(
    private val remoteDataSource: BackupRestoreRemoteDataSource,
    private val userRepository: UserRepository,
    private val musicRepository: MusicRepository,
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun createBackupArchive(options: BackupCreationOptions): DownloadedBackupArchive {
        return withContext(ioDispatcher) {
            val tempFile = File.createTempFile("ym_backup_", ".zip", context.cacheDir)
            try {
                val suggestedFileName = remoteDataSource.downloadBackupArchive(
                    options = options,
                    destinationFile = tempFile,
                )
                DownloadedBackupArchive(
                    file = tempFile,
                    suggestedFileName = sanitizeBackupFileName(suggestedFileName),
                )
            } catch (error: Exception) {
                tempFile.delete()
                throw error
            }
        }
    }

    suspend fun saveBackupArchive(archive: DownloadedBackupArchive, destinationUri: Uri) {
        withContext(ioDispatcher) {
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                archive.file.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Unable to open backup destination")
        }
    }

    suspend fun discardBackupArchive(archive: DownloadedBackupArchive) {
        withContext(ioDispatcher) {
            archive.file.delete()
        }
    }

    suspend fun startRestore(sourceUri: Uri): String {
        return withContext(ioDispatcher) {
            val tempFile = createTempArchiveCopy(sourceUri)
            try {
                remoteDataSource.startRestore(tempFile)
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun getRestoreStatus(restoreId: String): RestoreOperationStatus {
        return remoteDataSource.getRestoreStatus(restoreId)
    }

    suspend fun refreshLocalStateAfterRestore() {
        userRepository.updateLocalUserFromNetwork()
        musicRepository.loadLibrary(refreshFromNetwork = true)
    }

    private fun createTempArchiveCopy(sourceUri: Uri): File {
        val extension = archiveExtension(sourceUri)
        val tempFile = File.createTempFile("ym_restore_", extension, context.cacheDir)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open selected backup archive")
        return tempFile
    }

    private fun archiveExtension(sourceUri: Uri): String {
        val displayName = queryDisplayName(sourceUri)
        return if (displayName.endsWith(".zip", ignoreCase = true)) {
            ".zip"
        } else {
            ".tmp"
        }
    }

    private fun sanitizeBackupFileName(serverFileName: String?): String {
        val candidate = serverFileName
            ?.substringAfterLast('/')
            ?.trim()
            ?.trim('"')
            ?.replace(INVALID_FILE_NAME_CHARACTERS, "-")
            ?.takeIf { it.isNotBlank() }
            ?: defaultBackupFileName()
        return if (candidate.endsWith(".zip", ignoreCase = true)) {
            candidate
        } else {
            "$candidate.zip"
        }
    }

    private fun defaultBackupFileName(): String {
        return "ym-backup-${
            LocalDateTime.now().format(DEFAULT_FILE_NAME_FORMATTER)
        }.zip"
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index).orEmpty()
                }
            }
        return uri.lastPathSegment.orEmpty()
    }

    private companion object {
        private val INVALID_FILE_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|]")
        private val DEFAULT_FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
